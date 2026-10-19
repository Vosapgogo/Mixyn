package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mixyn.app.R;

/**
 * An empty top, bottom or shoes slot on the canvas: a dashed outline where the piece would be, with a
 * small + and a line like "Add shoes to complete the look". When the closet has such pieces but the
 * filter hides them all, it says so instead ("No shoes match your filter"), without the +. Only the
 * texts change per slot.
 */
public class SlotPlaceholderView extends LinearLayout {

    private final View plus;
    private final TextView text;
    @Nullable
    private final String addText;
    @Nullable
    private final String noMatchText;

    public SlotPlaceholderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundResource(R.drawable.bg_slot_placeholder);
        int padding = getResources().getDimensionPixelSize(R.dimen.space_m);
        setPadding(padding, padding, padding, padding);
        LayoutInflater.from(context).inflate(R.layout.view_slot_placeholder, this, true);

        plus = findViewById(R.id.placeholder_plus);
        text = findViewById(R.id.placeholder_text);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SlotPlaceholderView);
        addText = a.getString(R.styleable.SlotPlaceholderView_android_text);
        noMatchText = a.getString(R.styleable.SlotPlaceholderView_noMatchText);
        a.recycle();
        showNoMatch(false);
    }

    /** True: the filter hides this slot's pieces. False: the closet has none, and the + adds one. */
    public void showNoMatch(boolean noMatch) {
        text.setText(noMatch ? noMatchText : addText);
        plus.setVisibility(noMatch ? GONE : VISIBLE);
    }
}
