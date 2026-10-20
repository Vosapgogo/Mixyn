package com.mixyn.app.session;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.mixyn.app.api.dto.AuthResponse;
import com.mixyn.app.api.dto.UserDto;

/** Remembers who is logged in on this phone. Never stores the password — only the session token. */
public class SessionStore {

    private static final String PREFS = "mixyn_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "user_name";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_CITY = "user_city";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void save(AuthResponse auth) {
        prefs.edit()
                .putLong(KEY_USER_ID, auth.user.id)
                .putString(KEY_NAME, auth.user.name)
                .putString(KEY_EMAIL, auth.user.email)
                .putString(KEY_CITY, auth.user.city)
                .putString(KEY_TOKEN, auth.token)
                .apply();
    }

    /** The profile changed (Edit profile, City for weather): the name and city shown elsewhere follow. */
    public void update(UserDto user) {
        prefs.edit()
                .putString(KEY_NAME, user.name)
                .putString(KEY_CITY, user.city)
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getCity() {
        return prefs.getString(KEY_CITY, "");
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
