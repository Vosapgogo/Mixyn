package com.mixyn.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.mixyn.app.R;

/**
 * Header for top-level tabs: serif title + up to two icon actions (the second one sits before the main
 * one). The main action can show a small dot, for "something is switched on here". Only the texts and
 * icons change per screen.
 */
public class MixynLogoHeader extends LinearLayout {

    private final TextView title;
    private final ImageButton action;
    private final ImageButton secondAction;
    private final View badge;

    public MixynLogoHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_logo_header, this, true);
        title = findViewById(R.id.header_title);
        action = findViewById(R.id.header_action);
        secondAction = findViewById(R.id.header_second_action);
        badge = findViewById(R.id.header_badge);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MixynLogoHeader);
        title.setText(a.getString(R.styleable.MixynLogoHeader_headerTitle));
        Drawable icon = a.getDrawable(R.styleable.MixynLogoHeader_actionIcon);
        if (icon != null) {
            action.setImageDrawable(icon);
            action.setContentDescription(a.getString(R.styleable.MixynLogoHeader_actionDescription));
            findViewById(R.id.header_action_holder).setVisibility(VISIBLE);
        }
        Drawable secondIcon = a.getDrawable(R.styleable.MixynLogoHeader_secondActionIcon);
        if (secondIcon != null) {
            secondAction.setImageDrawable(secondIcon);
            secondAction.setContentDescription(a.getString(R.styleable.MixynLogoHeader_secondActionDescription));
            secondAction.setVisibility(VISIBLE);
        }
        a.recycle();
    }

    public void setTitle(CharSequence text) {
        title.setText(text);
    }

    public void setOnActionClickListener(OnClickListener listener) {
        action.setOnClickListener(listener);
    }

    public void setOnSecondActionClickListener(OnClickListener listener) {
        secondAction.setOnClickListener(listener);
    }

    /** Shows or hides the dot on the main action. */
    public void setActionBadge(boolean on) {
        badge.setVisibility(on ? VISIBLE : GONE);
    }
}
