package com.mixyn.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.api.dto.WeatherDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.CityPicker;
import com.mixyn.app.ui.components.MixynTextField;

/**
 * City for weather (from the profile): the city the stylist and the canvas dress the user for, today's
 * weather there, and another city to choose from the suggestions. Saving the city loads its weather.
 */
public class CityWeatherActivity extends BaseActivity {

    private MixynApi api;
    private MixynTextField cityField;
    private CityPicker cityPicker;
    /** The profile as it is, sent back whole with the new city. */
    @Nullable
    private UserDto user;

    public static void start(Context context) {
        context.startActivity(new Intent(context, CityWeatherActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city_weather);
        api = ApiClient.get(this);
        cityField = findViewById(R.id.weather_city_field);
        cityPicker = new CityPicker(this, cityField);
        ((TextView) findViewById(R.id.weather_city)).setText(new SessionStore(this).getCity());
        findViewById(R.id.weather_save).setOnClickListener(v -> save());

        api.me().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto me) {
                if (me == null) return;
                user = me;
                ((TextView) findViewById(R.id.weather_city)).setText(cityLabel(me));
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(CityWeatherActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        loadWeather();
    }

    @Override
    protected void onDestroy() {
        cityPicker.release();
        super.onDestroy();
    }

    private void loadWeather() {
        findViewById(R.id.weather_loading).setVisibility(View.VISIBLE);
        findViewById(R.id.weather_now).setVisibility(View.GONE);
        findViewById(R.id.weather_unavailable).setVisibility(View.GONE);
        api.weather().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable WeatherDto now) {
                findViewById(R.id.weather_loading).setVisibility(View.GONE);
                if (now == null) {
                    findViewById(R.id.weather_unavailable).setVisibility(View.VISIBLE);
                    return;
                }
                ((TextView) findViewById(R.id.weather_temperature))
                        .setText(getString(R.string.city_weather_now, now.temperature));
                TextView feel = findViewById(R.id.weather_feel);
                feel.setText(getString(R.string.city_weather_feel, capitalized(now.sky), now.feel));
                // The sun for a clear sky, the cloud for everything else.
                boolean clear = "clear".equals(now.sky) || "partly cloudy".equals(now.sky);
                feel.setCompoundDrawablesRelativeWithIntrinsicBounds(clear ? R.drawable.ic_sun : R.drawable.ic_cloud,
                        0, 0, 0);
                findViewById(R.id.weather_now).setVisibility(View.VISIBLE);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.weather_loading).setVisibility(View.GONE);
                findViewById(R.id.weather_unavailable).setVisibility(View.VISIBLE);
            }
        });
    }

    /** Only a city picked from the suggestions can be saved: the weather needs where it is. */
    private void save() {
        CityDto city = cityPicker.getSelected();
        if (city == null) {
            cityField.setError(getString(R.string.error_city_required));
            return;
        }
        if (user == null) return; // the profile hasn't arrived: it is sent back whole
        setSaving(true);
        api.updateProfile(EditProfileActivity.profile(user.name, user.username, city)).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto saved) {
                setSaving(false);
                if (saved == null) return;
                user = saved;
                new SessionStore(CityWeatherActivity.this).update(saved);
                ((TextView) findViewById(R.id.weather_city)).setText(cityLabel(saved));
                cityField.setValue("");
                Toast.makeText(CityWeatherActivity.this, R.string.city_saved, Toast.LENGTH_SHORT).show();
                loadWeather();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setSaving(false);
                cityField.setError(message);
            }
        });
    }

    private void setSaving(boolean saving) {
        MaterialButton save = findViewById(R.id.weather_save);
        save.setEnabled(!saving);
        save.setText(saving ? "" : getString(R.string.action_save_city));
        findViewById(R.id.weather_saving).setVisibility(saving ? View.VISIBLE : View.GONE);
    }

    private static String cityLabel(UserDto user) {
        return user.country == null ? user.city : user.city + ", " + user.country;
    }

    private static String capitalized(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
