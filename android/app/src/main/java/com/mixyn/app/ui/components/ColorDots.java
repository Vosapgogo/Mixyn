package com.mixyn.app.ui.components;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.annotation.ColorInt;
import androidx.annotation.DimenRes;
import androidx.core.content.ContextCompat;

import com.mixyn.app.R;

/** Small round color swatches. The thin outline keeps white and cream visible on light backgrounds. */
public final class ColorDots {

    private ColorDots() {
    }

    public static Drawable dot(Context context, @ColorInt int color, @DimenRes int sizeRes) {
        int size = context.getResources().getDimensionPixelSize(sizeRes);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(context.getResources().getDimensionPixelSize(R.dimen.stroke),
                ContextCompat.getColor(context, R.color.line));
        dot.setSize(size, size);
        return dot;
    }
}
