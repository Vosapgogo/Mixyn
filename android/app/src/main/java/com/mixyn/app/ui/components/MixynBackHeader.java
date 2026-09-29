package com.mixyn.app.ui.components;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.mixyn.app.R;

/** Header for inner screens: back button + serif title + subtitle. Only the texts change per screen. */
public class MixynBackHeader extends LinearLayout {

    private final TextView title;
    private final TextView subtitle;

    public MixynBackHeader(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.view_back_header, this, true);
        title = findViewById(R.id.header_title);
        subtitle = findViewById(R.id.header_subtitle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MixynBackHeader);
        setTexts(a.getString(R.styleable.MixynBackHeader_title), a.getString(R.styleable.MixynBackHeader_subtitle));
        a.recycle();

        findViewById(R.id.header_back).setOnClickListener(v -> {
            if (getContext() instanceof ComponentActivity activity) {
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }

    public void setTexts(@StringRes int titleRes, @StringRes int subtitleRes) {
        setTexts(getContext().getString(titleRes), getContext().getString(subtitleRes));
    }

    public void setTexts(@Nullable CharSequence titleText, @Nullable CharSequence subtitleText) {
        title.setText(titleText);
        subtitle.setText(subtitleText);
        subtitle.setVisibility(subtitleText == null ? GONE : VISIBLE);
    }
}
