package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mixyn.app.R;

/**
 * An empty top, bottom or shoes slot on the canvas: a dashed outline where the piece would be, with a
 * small + and a line like "Add shoes to complete the look". Only the text changes per slot.
 */
public class SlotPlaceholderView extends LinearLayout {

    public SlotPlaceholderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundResource(R.drawable.bg_slot_placeholder);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_m);
        setPadding(padding, padding, padding, padding);
        LayoutInflater.from(context).inflate(R.layout.view_slot_placeholder, this, true);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlotPlaceholderView);
        ((TextView) findViewById(R.id.placeholder_text)).setText(a.getString(R.styleable.SlotPlaceholderView_android_text));
        a.recycle();
    }
}
