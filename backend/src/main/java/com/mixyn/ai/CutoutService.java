package com.mixyn.ai;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Cuts a piece out of its photo.
 *
 * Normally that is just the background-removal tool's own cutout. But on a piece close in color to the
 * backdrop (a cream striped beanie on white) the model is unsure about most of the piece: whole stripes
 * come out half see-through, and darkened, because the tool dims whatever it is unsure about. Such a
 * cutout is repaired: a second model, which fails in other places, is asked for its mask, the stronger
 * answer wins at every pixel, "probably the piece" becomes fully opaque (clothing is solid), stray
 * specks are dropped, and the colors are taken from the photo itself.
 *
 * The repair is kept for cutouts that need it. Used on every photo it leaves patches of backdrop
 * around pieces the first model had cut out cleanly, and it doubles the waiting time.
 */
@Service
public class CutoutService {

    /** The second opinion. Like u2net it is about 170 MB, Apache 2.0, and baked into the rembg image. */
    static final String SECOND_MODEL = "isnet-general-use";

    /**
     * A cutout needs repair when the model was unsure about this much of the piece. A clean cutout is
     * unsure only along its edge (a few percent; under a third even for a bag on a thin chain), the
     * striped beanie was unsure about two thirds.
     */
    static final double UNSURE_LIMIT = 0.45;
    /** Mask values (0–255): above BARELY the model saw something, from SURE up it was sure. */
    static final int BARELY = 20;
    static final int SURE = 235;

    /** Repair: values up to LOW become background, from HIGH up the piece; between them the edge stays soft. */
    static final int LOW = 20;
    static final int HIGH = 60;
    /** A separate blob is kept if it is at least this share of the largest one (the second shoe of a pair)… */
    static final int MIN_BLOB_PERCENT = 2;
    /** …and solid on average, not a faint smudge the models weren't sure about. */
    static final int MIN_BLOB_ALPHA = 128;

    private static final Logger log = LoggerFactory.getLogger(CutoutService.class);

    private final RembgClient rembg;

    public CutoutService(RembgClient rembg) {
        this.rembg = rembg;
    }

    /** PNG bytes of the photo with its background made transparent. */
    public byte[] cutOut(byte[] photo, String filename) {
        byte[] cutout = rembg.removeBackground(photo, filename);
        BufferedImage first = read(cutout);
        BufferedImage picture = read(photo); // null for a format Java can't decode (HEIC from another client)
        if (first == null || picture == null
                || first.getWidth() != picture.getWidth() || first.getHeight() != picture.getHeight()) {
            return cutout;
        }
        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] alpha = first.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] >>>= 24;
        }
        double unsure = unsureShare(alpha);
        if (unsure < UNSURE_LIMIT) {
            return cutout; // the usual case: nothing to repair
        }
        int[] second = mask(photo, filename, SECOND_MODEL, width, height);
        if (second == null) {
            return cutout;
        }
        log.info("The first model was unsure about {}% of the piece; repairing the cutout with {}",
                Math.round(unsure * 100), SECOND_MODEL);
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] = Math.max(alpha[i], second[i]);
        }
        solidify(alpha, width, height);

        int[] pixels = picture.getRGB(0, 0, width, height, null, 0, width);
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (alpha[i] << 24) | (pixels[i] & 0xFFFFFF);
        }
        BufferedImage repaired = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        repaired.setRGB(0, 0, width, height, pixels, 0, width);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(repaired, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            return cutout;
        }
    }

    /** Of the pixels where the model saw anything at all, the share it wasn't sure about. */
    static double unsureShare(int[] alpha) {
        long seen = 0;
        long unsure = 0;
        for (int a : alpha) {
            if (a > BARELY) {
                seen++;
                if (a < SURE) unsure++;
            }
        }
        return seen == 0 ? 0 : (double) unsure / seen;
    }

    /**
     * One model's mask as 0–255 per pixel, or null if the model failed or its mask doesn't fit the
     * picture (the tool turns a photo by its EXIF orientation and Java doesn't; the app's own uploads
     * carry none).
     */
    private int[] mask(byte[] photo, String filename, String model, int width, int height) {
        try {
            BufferedImage mask = read(rembg.mask(photo, filename, model));
            if (mask == null || mask.getWidth() != width || mask.getHeight() != height) {
                log.warn("The {} mask doesn't fit the photo; ignoring it", model);
                return null;
            }
            int[] values = mask.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < values.length; i++) {
                values[i] &= 0xFF; // a gray picture: any channel is the mask value
            }
            return values;
        } catch (RuntimeException e) {
            log.warn("Background model {} failed: {}", model, e.toString());
            return null;
        }
    }

    /**
     * Turns a soft, unsure mask into the mask of a solid object, in place: values are stretched so that
     * LOW and below become 0 and HIGH and above 255, then every separate blob that is small next to the
     * largest one, or faint on average, is cleared.
     */
    static void solidify(int[] alpha, int width, int height) {
        for (int i = 0; i < alpha.length; i++) {
            int a = alpha[i];
            alpha[i] = a <= LOW ? 0 : a >= HIGH ? 255 : (a - LOW) * 255 / (HIGH - LOW);
        }
        // Label the blobs: label[i] is the blob a visible pixel belongs to.
        int[] label = new int[alpha.length];
        int[] stack = new int[alpha.length];
        java.util.ArrayList<long[]> blobs = new java.util.ArrayList<>(); // {area, sum of alpha}
        for (int start = 0; start < alpha.length; start++) {
            if (alpha[start] == 0 || label[start] != 0) {
                continue;
            }
            int id = blobs.size() + 1;
            long area = 0;
            long sum = 0;
            int top = 0;
            stack[top++] = start;
            label[start] = id;
            while (top > 0) {
                int p = stack[--top];
                area++;
                sum += alpha[p];
                int x = p % width;
                if (x > 0) top = visit(p - 1, id, alpha, label, stack, top);
                if (x < width - 1) top = visit(p + 1, id, alpha, label, stack, top);
                if (p >= width) top = visit(p - width, id, alpha, label, stack, top);
                if (p < alpha.length - width) top = visit(p + width, id, alpha, label, stack, top);
            }
            blobs.add(new long[] {area, sum});
        }
        long largest = blobs.stream().mapToLong(blob -> blob[0]).max().orElse(0);
        for (int i = 0; i < alpha.length; i++) {
            if (label[i] == 0) {
                continue;
            }
            long[] blob = blobs.get(label[i] - 1);
            boolean keep = blob[0] * 100 >= largest * MIN_BLOB_PERCENT && blob[1] >= blob[0] * MIN_BLOB_ALPHA;
            if (!keep) {
                alpha[i] = 0;
            }
        }
    }

    private static int visit(int p, int id, int[] alpha, int[] label, int[] stack, int top) {
        if (alpha[p] != 0 && label[p] == 0) {
            label[p] = id;
            stack[top++] = p;
        }
        return top;
    }

    private static BufferedImage read(byte[] bytes) {
        try {
            return bytes == null ? null : ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return null;
        }
    }
}
