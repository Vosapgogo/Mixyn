package com.mixyn.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.CityPicker;
import com.mixyn.app.ui.components.MixynTextField;
import com.mixyn.app.ui.components.PhotoFiles;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Edit profile, from the pencil on the profile. The photo is saved as soon as it is picked; the name,
 * username, city and a new password with Save changes. The form is checked on the phone first, like
 * sign-up, and what only the backend can know (a username someone has, a wrong current password) comes
 * back under its field too.
 */
public class EditProfileActivity extends BaseActivity {

    /** The backend's rule for a username, checked here first. */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._]{3,30}");
    private static final int MIN_PASSWORD = 8;
    /** A profile photo needs no more than this; the backend keeps a 512 px square of it. */
    private static final int AVATAR_UPLOAD_SIDE = 1024;

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private MixynApi api;
    private MixynTextField name;
    private MixynTextField username;
    private MixynTextField city;
    private MixynTextField currentPassword;
    private MixynTextField newPassword;
    private CityPicker cityPicker;

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) uploadPhoto(uri);
            });

    public static void start(Context context) {
        context.startActivity(new Intent(context, EditProfileActivity.class));
    }

    /** The body of PUT /api/me: the whole profile, with a city picked from the suggestions. */
    static Map<String, Object> profile(String name, @Nullable String username, CityDto city) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("username", username);
        profile.put("city", city.name);
        profile.put("country", city.country);
        profile.put("latitude", city.latitude);
        profile.put("longitude", city.longitude);
        return profile;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);
        api = ApiClient.get(this);
        name = findViewById(R.id.edit_name);
        username = findViewById(R.id.edit_username);
        city = findViewById(R.id.edit_city);
        currentPassword = findViewById(R.id.edit_current_password);
        newPassword = findViewById(R.id.edit_new_password);
        cityPicker = new CityPicker(this, city);

        View.OnClickListener pickPhoto = v -> photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        findViewById(R.id.edit_avatar).setOnClickListener(pickPhoto);
        findViewById(R.id.edit_change_photo).setOnClickListener(pickPhoto);
        findViewById(R.id.edit_save).setOnClickListener(v -> save());

        api.me().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto me) {
                if (me == null) return;
                name.setValue(me.name);
                username.setValue(me.username == null ? "" : me.username);
                if (me.latitude != null && me.longitude != null) cityPicker.setSelected(cityOf(me));
                showAvatar(me.avatarUrl);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                showError(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        cityPicker.release();
        background.shutdown();
        super.onDestroy();
    }

    // ----- The photo -----

    private void uploadPhoto(Uri uri) {
        setPhotoLoading(true);
        // Reading and shrinking the photo is slow work, so it stays off the main (UI) thread.
        background.execute(() -> {
            byte[] jpeg = PhotoFiles.readAsJpeg(this, uri, AVATAR_UPLOAD_SIDE);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (jpeg == null) {
                    setPhotoLoading(false);
                    Toast.makeText(this, R.string.error_photo_unreadable, Toast.LENGTH_LONG).show();
                    return;
                }
                MultipartBody.Part part = MultipartBody.Part.createFormData("photo", "photo.jpg",
                        RequestBody.create(MediaType.parse("image/jpeg"), jpeg));
                api.uploadAvatar(part).enqueue(new ApiCallback<>(this) {
                    @Override
                    protected void onSuccess(@Nullable UserDto saved) {
                        setPhotoLoading(false);
                        if (saved != null) showAvatar(saved.avatarUrl);
                    }

                    @Override
                    protected void onError(@Nullable String code, @NonNull String message) {
                        setPhotoLoading(false);
                        Toast.makeText(EditProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private void showAvatar(@Nullable String url) {
        if (url == null) return;
        ImageView avatar = findViewById(R.id.edit_avatar);
        avatar.setPadding(0, 0, 0, 0);
        avatar.setImageTintList(null);
        Glide.with(this).load(ApiClient.imageUrl(url)).circleCrop().into(avatar);
    }

    private void setPhotoLoading(boolean loading) {
        findViewById(R.id.edit_avatar_progress).setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.edit_change_photo).setEnabled(!loading);
    }

    // ----- Save changes -----

    private void save() {
        showError(null);
        if (!validate()) return;
        setSaving(true);
        String typedUsername = normalizeUsername(username.getValue());
        api.updateProfile(profile(name.getValue(), typedUsername, cityPicker.getSelected())).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto saved) {
                if (saved != null) new SessionStore(EditProfileActivity.this).update(saved);
                if (wantsNewPassword()) changePassword();
                else done();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setSaving(false);
                if ("username_taken".equals(code)) username.setError(getString(R.string.error_username_taken));
                else if ("invalid_username".equals(code)) username.setError(getString(R.string.error_username_invalid));
                else showError(message);
            }
        });
    }

    private void changePassword() {
        Map<String, String> passwords = new HashMap<>();
        passwords.put("currentPassword", currentPassword.getValue());
        passwords.put("newPassword", newPassword.getValue());
        api.changePassword(passwords).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable Void nothing) {
                done();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setSaving(false);
                if ("wrong_password".equals(code)) currentPassword.setError(getString(R.string.error_current_password));
                else showError(message);
            }
        });
    }

    private void done() {
        Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Checks the form on the phone first, so obvious mistakes don't need a server round trip. */
    private boolean validate() {
        boolean ok = true;
        boolean nameOk = !name.getValue().isEmpty();
        name.setError(nameOk ? null : getString(R.string.error_name_required));
        ok &= nameOk;

        String typed = normalizeUsername(username.getValue());
        boolean usernameOk = typed == null || USERNAME.matcher(typed).matches();
        username.setError(usernameOk ? null : getString(R.string.error_username_invalid));
        ok &= usernameOk;

        boolean cityOk = cityPicker.getSelected() != null;
        city.setError(cityOk ? null : getString(R.string.error_city_required));
        ok &= cityOk;

        currentPassword.setError(null);
        newPassword.setError(null);
        if (wantsNewPassword()) {
            if (currentPassword.getValue().isEmpty()) {
                currentPassword.setError(getString(R.string.error_current_password_required));
                ok = false;
            }
            if (newPassword.getValue().length() < MIN_PASSWORD) {
                newPassword.setError(getString(R.string.error_password_short));
                ok = false;
            }
        }
        return ok;
    }

    /** A password is changed only when something is typed in one of the two fields. */
    private boolean wantsNewPassword() {
        return !currentPassword.getValue().isEmpty() || !newPassword.getValue().isEmpty();
    }

    /** "@Koko " → "koko"; an empty username is none. The backend does the same. */
    @Nullable
    private static String normalizeUsername(String typed) {
        String value = typed.trim();
        if (value.startsWith("@")) value = value.substring(1);
        value = value.toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private static CityDto cityOf(UserDto user) {
        CityDto city = new CityDto();
        city.name = user.city;
        city.country = user.country;
        city.latitude = user.latitude;
        city.longitude = user.longitude;
        return city;
    }

    private void showError(@Nullable String message) {
        TextView error = findViewById(R.id.edit_error);
        error.setText(message);
        error.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void setSaving(boolean saving) {
        MaterialButton save = findViewById(R.id.edit_save);
        save.setEnabled(!saving);
        save.setText(saving ? "" : getString(R.string.action_save_changes));
        findViewById(R.id.edit_progress).setVisibility(saving ? View.VISIBLE : View.GONE);
    }
}
