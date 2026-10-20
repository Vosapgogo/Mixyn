package com.mixyn.app.ui.components;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Gallery photos made ready to upload: any format the phone can open, scaled down, as JPEG. */
public final class PhotoFiles {

    private PhotoFiles() {
    }

    /**
     * Decodes any gallery photo (JPEG, PNG, HEIC…), scales it down so its long side is at most maxSide,
     * and re-encodes it as JPEG. Null if the photo can't be opened. Slow: call it off the main thread.
     */
    @Nullable
    public static byte[] readAsJpeg(Context context, Uri uri, int maxSide) {
        try {
            Bitmap bitmap;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(), uri),
                        (decoder, info, source) -> {
                            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                            int longSide = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                            if (longSide > maxSide) {
                                float scale = (float) maxSide / longSide;
                                decoder.setTargetSize(Math.round(info.getSize().getWidth() * scale),
                                        Math.round(info.getSize().getHeight() * scale));
                            }
                        });
            } else {
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
            }
            if (bitmap == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
