package com.mixyn.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mixyn.common.ApiException;

/**
 * Keeps cleaned item photos as PNG files in one folder; the database only stores the file name.
 * Before saving, the cutout is cropped to the item itself and scaled down, so it sits nicely in a grid.
 */
@Component
public class ImageStorage {

    private static final int MAX_SIDE = 1024;
    /** A profile photo is never larger than this square. */
    private static final int AVATAR_SIDE = 512;
    private static final int ALPHA_VISIBLE = 24; // pixels fainter than this count as background
    private static final int PADDING = 8;
    private static final int BOX_MARGIN_PERCENT = 3;
    /** A box narrower or shorter than 1/10 of the photo is not trusted. */
    private static final int BOX_MIN_SHARE = 10;

    private final Path dir;

    public ImageStorage(@Value("${mixyn.image-dir}") String imageDir) {
        this.dir = Path.of(imageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't create image folder " + dir, e);
        }
    }

    public Path getDir() {
        return dir;
    }

    /**
     * Cuts a cutout down to one part of it: used when the piece was photographed on a person, to keep
     * the piece and drop the rest of the body. The rectangle is given in thousandths of the image
     * (see TaggingService.Box) and gets a small margin, because the AI's boxes are a little tight.
     * A box too small to be a real piece is ignored and the cutout comes back unchanged.
     */
    public byte[] cropToBox(byte[] transparentPng, int top, int left, int bottom, int right, int scale) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(transparentPng));
            if (image == null) {
                throw invalidImage();
            }
            int marginX = image.getWidth() * BOX_MARGIN_PERCENT / 100;
            int marginY = image.getHeight() * BOX_MARGIN_PERCENT / 100;
            int x0 = Math.max(0, image.getWidth() * left / scale - marginX);
            int y0 = Math.max(0, image.getHeight() * top / scale - marginY);
            int x1 = Math.min(image.getWidth(), image.getWidth() * right / scale + marginX);
            int y1 = Math.min(image.getHeight(), image.getHeight() * bottom / scale + marginY);
            if (x1 - x0 < image.getWidth() / BOX_MIN_SHARE || y1 - y0 < image.getHeight() / BOX_MIN_SHARE) {
                return transparentPng;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image.getSubimage(x0, y0, x1 - x0, y1 - y0), "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw invalidImage();
        }
    }

    /** A cutout ready to store: PNG bytes of the cropped, resized item. */
    public byte[] cropToItem(byte[] transparentPng) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(transparentPng));
            if (image == null) {
                throw invalidImage();
            }
            int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) > ALPHA_VISIBLE) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0) {
                throw invalidImage(); // everything was removed: no item found
            }
            minX = Math.max(0, minX - PADDING);
            minY = Math.max(0, minY - PADDING);
            maxX = Math.min(image.getWidth() - 1, maxX + PADDING);
            maxY = Math.min(image.getHeight() - 1, maxY + PADDING);
            BufferedImage cropped = image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);

            double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(cropped.getWidth(), cropped.getHeight()));
            int w = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(cropped, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw invalidImage();
        }
    }

    /**
     * A profile photo ready to store: the middle square of the photo (JPEG or PNG), at most AVATAR_SIDE
     * pixels wide, as PNG bytes.
     */
    public byte[] squareAvatar(byte[] photo) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo));
            if (image == null) {
                throw notAPhoto();
            }
            int side = Math.min(image.getWidth(), image.getHeight());
            BufferedImage square = image.getSubimage((image.getWidth() - side) / 2, (image.getHeight() - side) / 2,
                    side, side);
            int size = Math.min(side, AVATAR_SIDE);
            BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(square, 0, 0, size, size, null);
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw notAPhoto();
        }
    }

    private static ApiException notAPhoto() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_image", "This file isn't a photo we can read");
    }

    /** Turns a PNG clockwise by 90, 180 or 270 degrees ("Edit photo" on the item card). */
    public byte[] rotate(byte[] png, int degrees) {
        if (degrees % 360 == 0) {
            return png;
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            boolean sideways = degrees % 180 != 0;
            int w = sideways ? source.getHeight() : source.getWidth();
            int h = sideways ? source.getWidth() : source.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.translate(w / 2.0, h / 2.0);
            g.rotate(Math.toRadians(degrees));
            g.drawImage(source, -source.getWidth() / 2, -source.getHeight() / 2, null);
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            return png; // better an unrotated photo than a failed upload
        }
    }

    /** Flips a PNG left to right. */
    public byte[] mirror(byte[] png) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.drawImage(source, source.getWidth(), 0, -source.getWidth(), source.getHeight(), null);
            g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(out, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            return png;
        }
    }

    /** Saves the PNG and returns its file name, e.g. "3f2a….png". */
    public String save(byte[] png) {
        String fileName = UUID.randomUUID() + ".png";
        try {
            Files.write(dir.resolve(fileName), png);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't save image " + fileName, e);
        }
        return fileName;
    }

    public byte[] read(String fileName) {
        try {
            return Files.readAllBytes(dir.resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("Can't read image " + fileName, e);
        }
    }

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(dir.resolve(fileName));
        } catch (IOException ignored) {
            // a leftover file is harmless
        }
    }

    private static ApiException invalidImage() {
        return new ApiException(HttpStatus.BAD_REQUEST, "invalid_image",
                "We couldn't find a clothing item in this photo");
    }
}
