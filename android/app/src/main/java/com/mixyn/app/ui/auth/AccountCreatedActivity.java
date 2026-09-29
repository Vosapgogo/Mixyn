package com.mixyn.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.main.MainActivity;

/** Shown once after Create account: a warm welcome before the (empty) wardrobe. */
public class AccountCreatedActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_created);

        String name = new SessionStore(this).getName();
        ((TextView) findViewById(R.id.created_title)).setText(getString(R.string.account_created_title, name));

        // Both lead to the wardrobe for now; "Let's go" will open Add clothes once uploads exist (slice 2).
        findViewById(R.id.created_go).setOnClickListener(v -> openWardrobe());
        findViewById(R.id.created_later).setOnClickListener(v -> openWardrobe());

        // The account already exists, so Back also continues to the wardrobe instead of the form.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                openWardrobe();
            }
        });
    }

    private void openWardrobe() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
