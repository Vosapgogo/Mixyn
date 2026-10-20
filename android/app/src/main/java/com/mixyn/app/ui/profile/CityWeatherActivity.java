package com.mixyn.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.api.dto.WeatherDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * City for weather, from the profile (the learner's variant A): the whole page is today's weather in the
 * user's city — the city and the date, the sun or a cloud, the temperature, how it feels and what to wear
 * for it. Change city opens a sheet with the city suggestions; the new city is saved and its weather
 * loaded.
 */
public class CityWeatherActivity extends BaseActivity {

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

    private MixynApi api;
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
        ((TextView) findViewById(R.id.weather_city)).setText(new SessionStore(this).getCity());
        ((TextView) findViewById(R.id.weather_date)).setText(LocalDate.now().format(TODAY));
        findViewById(R.id.weather_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.weather_change_city).setOnClickListener(v ->
                new CitySheet().show(getSupportFragmentManager(), CitySheet.REQUEST_KEY));
        getSupportFragmentManager().setFragmentResultListener(CitySheet.REQUEST_KEY, this, (key, result) -> {
            CityDto city = new CityDto();
            city.name = result.getString(CitySheet.RESULT_NAME);
            city.country = result.getString(CitySheet.RESULT_COUNTRY);
            city.latitude = result.getDouble(CitySheet.RESULT_LATITUDE);
            city.longitude = result.getDouble(CitySheet.RESULT_LONGITUDE);
            save(city);
        });

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
                boolean clear = "clear".equals(now.sky) || "partly cloudy".equals(now.sky);
                ((ImageView) findViewById(R.id.weather_icon)).setImageResource(clear ? R.drawable.ic_sun : R.drawable.ic_cloud);
                ((TextView) findViewById(R.id.weather_temperature))
                        .setText(getString(R.string.city_weather_now, now.temperature));
                ((TextView) findViewById(R.id.weather_feel))
                        .setText(getString(R.string.city_weather_feel, capitalized(now.sky), now.feel));
                ((TextView) findViewById(R.id.weather_wear)).setText(whatToWear(now.feel));
                findViewById(R.id.weather_now).setVisibility(View.VISIBLE);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.weather_loading).setVisibility(View.GONE);
                findViewById(R.id.weather_unavailable).setVisibility(View.VISIBLE);
            }
        });
    }

    /** A line on what to wear, by how the weather feels. */
    @StringRes
    private static int whatToWear(@Nullable String feel) {
        if (feel == null) return R.string.weather_wear_mild;
        return switch (feel) {
            case "freezing" -> R.string.weather_wear_freezing;
            case "cold" -> R.string.weather_wear_cold;
            case "cool" -> R.string.weather_wear_cool;
            case "warm" -> R.string.weather_wear_warm;
            case "hot" -> R.string.weather_wear_hot;
            default -> R.string.weather_wear_mild;
        };
    }

    private void save(CityDto city) {
        if (user == null) return; // the profile hasn't arrived: it is sent back whole
        api.updateProfile(EditProfileActivity.profile(user.name, user.username, city)).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable UserDto saved) {
                if (saved == null) return;
                user = saved;
                new SessionStore(CityWeatherActivity.this).update(saved);
                ((TextView) findViewById(R.id.weather_city)).setText(cityLabel(saved));
                Toast.makeText(CityWeatherActivity.this, R.string.city_saved, Toast.LENGTH_SHORT).show();
                loadWeather();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(CityWeatherActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String cityLabel(UserDto user) {
        return user.country == null ? user.city : user.city + ", " + user.country;
    }

    private static String capitalized(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
