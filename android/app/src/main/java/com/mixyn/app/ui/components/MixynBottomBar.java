package com.mixyn.app.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mixyn.app.R;

import java.util.EnumMap;
import java.util.Map;

/** The app's bottom navigation. The hosting screen decides what each tap does. */
public class MixynBottomBar extends LinearLayout {

    public enum Tab { HOME, CALENDAR, FRIENDS, PROFILE }

    public interface Listener {
        void onTabClick(Tab tab);

        void onAddClick();
    }

    private final Map<Tab, TextView> items = new EnumMap<>(Tab.class);
    @Nullable
    private Listener listener;

    public MixynBottomBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_bottom_bar);
        LayoutInflater.from(context).inflate(R.layout.view_bottom_bar, this, true);

        items.put(Tab.HOME, findViewById(R.id.nav_home));
        items.put(Tab.CALENDAR, findViewById(R.id.nav_calendar));
        items.put(Tab.FRIENDS, findViewById(R.id.nav_friends));
        items.put(Tab.PROFILE, findViewById(R.id.nav_profile));
        items.forEach((tab, view) -> view.setOnClickListener(v -> {
            if (listener != null) listener.onTabClick(tab);
        }));
        findViewById(R.id.nav_add).setOnClickListener(v -> {
            if (listener != null) listener.onAddClick();
        });
        setSelectedTab(Tab.HOME);

        // The white bar continues under the system gesture area; its tabs stay above it.
        int barHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height);
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            setPadding(getPaddingLeft(), 0, getPaddingRight(), navBar);
            getLayoutParams().height = barHeight + navBar;
            requestLayout();
            return insets;
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Highlights one tab (ink); the others use the muted color. */
    public void setSelectedTab(Tab selected) {
        int on = ContextCompat.getColor(getContext(), R.color.ink);
        int off = ContextCompat.getColor(getContext(), R.color.ink_muted);
        items.forEach((tab, view) -> {
            int color = tab == selected ? on : off;
            view.setTextColor(color);
            view.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        });
    }
}
