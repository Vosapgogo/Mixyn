package com.mixyn.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mixyn.app.R;

/** Header for top-level tabs: serif title + one optional icon action. Only the texts/icon change per screen. */
public class MixynLogoHeader extends LinearLayout {

    private final TextView title;
    private final ImageButton action;

    public MixynLogoHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_logo_header, this, true);
        title = findViewById(R.id.header_title);
        action = findViewById(R.id.header_action);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MixynLogoHeader);
        title.setText(a.getString(R.styleable.MixynLogoHeader_headerTitle));
        Drawable icon = a.getDrawable(R.styleable.MixynLogoHeader_actionIcon);
        if (icon != null) {
            action.setImageDrawable(icon);
            action.setContentDescription(a.getString(R.styleable.MixynLogoHeader_actionDescription));
            action.setVisibility(VISIBLE);
        }
        a.recycle();
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
    }

    public void setOnActionClickListener(OnClickListener listener) {
        action.setOnClickListener(listener);
    }
}
