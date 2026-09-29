package com.mixyn.app.ui.base;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15+ draws apps edge to edge. Every Mixyn screen pads its content by the status bar,
 * navigation bar and keyboard, so nothing hides behind them.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            int bottom = drawsBehindNavigationBar()
                    ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    : bars.bottom;
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            // Pass the insets on, so a bottom bar can extend under the gesture area and pad itself.
            return insets;
        });
    }

    /** Screens with a bottom bar return true: the bar fills the area behind the navigation bar itself. */
    protected boolean drawsBehindNavigationBar() {
        return false;
    }
}
