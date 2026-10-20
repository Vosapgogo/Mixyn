package com.mixyn.app.ui.auth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.AuthResponse;
import com.mixyn.app.api.dto.LoginRequest;
import com.mixyn.app.api.dto.RegisterRequest;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.MixynBackHeader;
import com.mixyn.app.ui.components.CityPicker;
import com.mixyn.app.ui.components.MixynTextField;
import com.mixyn.app.ui.main.MainActivity;


import retrofit2.Call;

/** Create account and Log in share this screen; the mode decides which fields are shown. */
public class AuthActivity extends BaseActivity {

    public enum Mode { REGISTER, LOGIN }

    private static final String EXTRA_MODE = "mode";
    private static final String EXTRA_EMAIL = "email";
    private static final long CITY_SEARCH_DELAY_MS = 300;

    private Mode mode;
    private MixynApi api;
    private MixynTextField name;
    private MixynTextField email;
    private MixynTextField password;
    private MixynTextField city;
    private TextView formError;
    private TextView formErrorAction;
    private MaterialButton submit;
    private ProgressBar progress;

    // City autocomplete: the user must pick one of the suggestions.
    @Nullable
    private CityPicker cityPicker;

    public static void start(Activity from, Mode mode, @Nullable String email) {
        from.startActivity(new Intent(from, AuthActivity.class)
                .putExtra(EXTRA_MODE, mode.name())
                .putExtra(EXTRA_EMAIL, email));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        api = ApiClient.get(this);
        mode = Mode.valueOf(getIntent().getStringExtra(EXTRA_MODE));

        name = findViewById(R.id.auth_name);
        email = findViewById(R.id.auth_email);
        password = findViewById(R.id.auth_password);
        city = findViewById(R.id.auth_city);
        formError = findViewById(R.id.auth_error);
        formErrorAction = findViewById(R.id.auth_error_action);
        submit = findViewById(R.id.auth_submit);
        progress = findViewById(R.id.auth_progress);

        boolean register = mode == Mode.REGISTER;
        MixynBackHeader header = findViewById(R.id.auth_header);
        header.setTexts(register ? R.string.register_title : R.string.login_title,
                register ? R.string.register_subtitle : R.string.login_subtitle);
        name.setVisibility(register ? View.VISIBLE : View.GONE);
        city.setVisibility(register ? View.VISIBLE : View.GONE);
        password.getInput().setHint(register ? R.string.field_password_hint_register : R.string.field_password_hint_login);
        submit.setText(register ? R.string.action_create_account : R.string.action_log_in);
        ((TextView) findViewById(R.id.auth_switch_prompt))
                .setText(register ? R.string.register_switch_prompt : R.string.login_switch_prompt);
        TextView switchLink = findViewById(R.id.auth_switch);
        switchLink.setText(register ? R.string.action_log_in : R.string.action_create_account);
        switchLink.setOnClickListener(v -> switchMode());
        formErrorAction.setOnClickListener(v -> switchMode());

        String prefill = getIntent().getStringExtra(EXTRA_EMAIL);
        if (prefill != null) email.setValue(prefill);

        if (register) cityPicker = new CityPicker(this, city);
        submit.setOnClickListener(v -> submit());
    }

    private void switchMode() {
        start(this, mode == Mode.REGISTER ? Mode.LOGIN : Mode.REGISTER, email.getValue());
        finish();
    }

    // ----- Submit -----

    private void submit() {
        showFormError(null, false);
        if (!validate()) return;
        setLoading(true);
        Call<AuthResponse> call = mode == Mode.REGISTER
                ? api.register(new RegisterRequest(name.getValue(), email.getValue(), password.getValue(), cityPicker.getSelected()))
                : api.login(new LoginRequest(email.getValue(), password.getValue()));
        call.enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable AuthResponse auth) {
                setLoading(false);
                if (auth == null) return;
                new SessionStore(AuthActivity.this).save(auth);
                if (mode == Mode.REGISTER) {
                    startActivity(new Intent(AuthActivity.this, AccountCreatedActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                } else {
                    openWardrobe();
                }
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setLoading(false);
                if ("invalid_credentials".equals(code)) {
                    showFormError(getString(R.string.error_invalid_credentials), false);
                } else if ("email_taken".equals(code)) {
                    showFormError(getString(R.string.error_email_taken), true);
                } else {
                    showFormError(message, false);
                }
            }
        });
    }

    /** Checks the form on the phone first, so obvious mistakes don't need a server round trip. */
    private boolean validate() {
        boolean ok = true;
        if (mode == Mode.REGISTER) {
            name.setError(name.getValue().isEmpty() ? getString(R.string.error_name_required) : null);
            ok &= !name.getValue().isEmpty();
        }
        boolean emailOk = Patterns.EMAIL_ADDRESS.matcher(email.getValue()).matches();
        email.setError(emailOk ? null : getString(R.string.error_email_invalid));
        ok &= emailOk;

        String pw = password.getValue();
        String pwError = null;
        if (pw.isEmpty()) {
            pwError = getString(R.string.error_password_required);
        } else if (mode == Mode.REGISTER && pw.length() < 8) {
            pwError = getString(R.string.error_password_short);
        }
        password.setError(pwError);
        ok &= pwError == null;

        if (mode == Mode.REGISTER) {
            boolean picked = cityPicker != null && cityPicker.getSelected() != null;
            city.setError(picked ? null : getString(R.string.error_city_required));
            ok &= picked;
        }
        return ok;
    }

    private void showFormError(@Nullable String message, boolean offerLogin) {
        formError.setText(message);
        formError.setVisibility(message == null ? View.GONE : View.VISIBLE);
        formErrorAction.setVisibility(offerLogin ? View.VISIBLE : View.GONE);
    }

    private void setLoading(boolean loading) {
        submit.setEnabled(!loading);
        submit.setText(loading ? "" : getString(mode == Mode.REGISTER ? R.string.action_create_account : R.string.action_log_in));
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void openWardrobe() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    @Override
    protected void onDestroy() {
        if (cityPicker != null) cityPicker.release();
        super.onDestroy();
    }
}
