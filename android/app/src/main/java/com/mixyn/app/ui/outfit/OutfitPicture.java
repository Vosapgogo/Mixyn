package com.mixyn.app.ui.outfit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * The picture of an outfit as the canvas drew it: cut down to the outfit itself and kept as a PNG file
 * with a see-through background, until it is uploaded with the saved outfit.
 */
public final class OutfitPicture {

    private static final int ALPHA_VISIBLE = 24; // fainter pixels count as background, like on the backend
    private static final String PREFIX = "outfit-";

    private OutfitPicture() {
    }

    /**
     * Cuts the empty space around the outfit away and writes the rest into the app's cache folder.
     * Slow work (it goes through every pixel), so call it off the main thread.
     *
     * @return the file, or null when the picture is empty or can't be written
     */
    @Nullable
    public static File save(Context context, Bitmap canvas) {
        Bitmap outfit = trim(canvas);
        if (outfit == null) return null;
        File folder = context.getCacheDir();
        File[] earlier = folder.listFiles((dir, name) -> name.startsWith(PREFIX));
        if (earlier != null) {
            for (File file : earlier) file.delete(); // only the newest look is ever needed
        }
        try {
            File file = File.createTempFile(PREFIX, ".png", folder);
            try (FileOutputStream out = new FileOutputStream(file)) {
                outfit.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    /** The bytes of a saved picture, ready to upload; null if the file is gone. */
    @Nullable
    public static byte[] read(String path) {
        try {
            return Files.readAllBytes(new File(path).toPath());
        } catch (IOException e) {
            return null;
        }
    }

    /** The smallest part of the picture that still holds every visible pixel; null if there is none. */
    @Nullable
    private static Bitmap trim(Bitmap picture) {
        int width = picture.getWidth();
        int height = picture.getHeight();
        int[] row = new int[width];
        int left = width, top = height, right = -1, bottom = -1;
        for (int y = 0; y < height; y++) {
            picture.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                if (Color.alpha(row[x]) <= ALPHA_VISIBLE) continue;
                if (x < left) left = x;
                if (x > right) right = x;
                if (y < top) top = y;
                bottom = y;
            }
        }
        if (right < 0) return null;
        return Bitmap.createBitmap(picture, left, top, right - left + 1, bottom - top + 1);
    }
}
