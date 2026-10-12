package com.mixyn.app.ui.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.DimenRes;
import androidx.core.content.ContextCompat;

import com.mixyn.app.R;
import com.mixyn.app.api.dto.ColorDto;

import java.util.List;

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

    /** Fills a row with one dot per color, each overlapping the one before; earlier dots are replaced. */
    public static void fill(LinearLayout row, List<ColorDto> colors) {
        Context context = row.getContext();
        row.removeAllViews();
        for (ColorDto color : colors) {
            ImageView dot = new ImageView(context);
            dot.setImageDrawable(dot(context, Color.parseColor(color.hex), R.dimen.swatch_size));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (row.getChildCount() > 0) {
                params.setMarginStart(context.getResources().getDimensionPixelSize(R.dimen.swatch_overlap));
            }
            row.addView(dot, params);
        }
    }
}
