package com.mixyn.app.ui.welcome;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.ui.auth.AuthActivity;
import com.mixyn.app.ui.base.BaseActivity;

/** First screen for someone who isn't logged in. */
public class WelcomeActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);
        findViewById(R.id.welcome_create).setOnClickListener(v -> AuthActivity.start(this, AuthActivity.Mode.REGISTER, null));
        findViewById(R.id.welcome_login).setOnClickListener(v -> AuthActivity.start(this, AuthActivity.Mode.LOGIN, null));
    }
}
