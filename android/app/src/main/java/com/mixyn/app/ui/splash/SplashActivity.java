package com.mixyn.app.ui.splash;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.main.MainActivity;
import com.mixyn.app.ui.welcome.WelcomeActivity;

/** Shown on every launch; then goes to the wardrobe (logged in) or Welcome. */
public class SplashActivity extends BaseActivity {

    private static final long MIN_SHOW_MS = 1000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        new Handler(Looper.getMainLooper()).postDelayed(this::route, MIN_SHOW_MS);
    }

    private void route() {
        SessionStore session = new SessionStore(this);
        if (!session.isLoggedIn()) {
            open(WelcomeActivity.class);
            return;
        }
        // Confirm the saved session still works. A 401 is handled by ApiCallback (→ Welcome).
        ApiClient.get(this).me().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto user) {
                open(MainActivity.class);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                // Server unreachable: still open the wardrobe; its screens show the connection error.
                open(MainActivity.class);
            }
        });
    }

    private void open(Class<?> screen) {
        startActivity(new Intent(this, screen));
        finish();
    }
}
