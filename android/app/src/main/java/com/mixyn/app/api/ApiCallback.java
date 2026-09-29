package com.mixyn.app.api;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.mixyn.app.R;
import com.mixyn.app.api.dto.ApiError;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.welcome.WelcomeActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Turns Retrofit's two callbacks into success / friendly error. A 401 on a logged-in call means the
 * session is gone, so it clears it and returns to Welcome.
 */
public abstract class ApiCallback<T> implements Callback<T> {

    private final Activity activity;

    protected ApiCallback(Activity activity) {
        this.activity = activity;
    }

    protected abstract void onSuccess(@Nullable T body);

    /** @param code the backend error code ("email_taken"…), or null for network problems */
    protected abstract void onError(@Nullable String code, @NonNull String message);

    @Override
    public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (response.isSuccessful()) {
            onSuccess(response.body());
            return;
        }
        ApiError error = parseError(response);
        String code = error != null ? error.error : null;
        SessionStore session = new SessionStore(activity);
        if (response.code() == 401 && "unauthorized".equals(code) && session.isLoggedIn()) {
            session.clear();
            activity.startActivity(new Intent(activity, WelcomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return;
        }
        String message = error != null && error.message != null
                ? error.message : activity.getString(R.string.error_generic);
        onError(code, message);
    }

    @Override
    public void onFailure(@NonNull Call<T> call, @NonNull Throwable t) {
        android.util.Log.w("MixynApi", "Request failed: " + call.request().url(), t);
        if (activity.isFinishing() || activity.isDestroyed() || call.isCanceled()) return;
        onError(null, activity.getString(R.string.error_network));
    }

    @Nullable
    private static ApiError parseError(Response<?> response) {
        try (var body = response.errorBody()) {
            return body == null ? null : new Gson().fromJson(body.charStream(), ApiError.class);
        } catch (Exception e) {
            return null;
        }
    }
}
