package com.mixyn.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.welcome.WelcomeActivity;

/**
 * After logging out or deleting the account: "You're logged out" / "Your account is deleted", "We hope to
 * see you again soon", and Back to start (Welcome, where one can create an account or log in). The
 * screens of the account are gone from the back stack by then.
 */
public class GoodbyeActivity extends BaseActivity {

    private static final String EXTRA_DELETED = "deleted";

    /** @param deleted the account was deleted, rather than logged out of */
    public static void start(Context context, boolean deleted) {
        context.startActivity(new Intent(context, GoodbyeActivity.class)
                .putExtra(EXTRA_DELETED, deleted)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goodbye);
        boolean deleted = getIntent().getBooleanExtra(EXTRA_DELETED, false);
        ((TextView) findViewById(R.id.goodbye_title))
                .setText(deleted ? R.string.goodbye_deleted_title : R.string.goodbye_logged_out_title);
        findViewById(R.id.goodbye_start).setOnClickListener(v -> backToStart());
        // Back leads to the start as well: there is no account to go back to.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                backToStart();
            }
        });
    }

    private void backToStart() {
        startActivity(new Intent(this, WelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }
}
