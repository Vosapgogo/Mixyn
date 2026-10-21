package com.mixyn.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.ForecastDto;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * City for weather, from the profile. Today's weather in the user's city on a mocha card (the city, the
 * date, a picture of the weather, the temperature, how it feels, wind, humidity and the chance of rain
 * for the rest of the day), then tips for the day (what to wear, and an umbrella or a raincoat when rain
 * is coming: see WeatherAdvice), the next 24 hours and the next 7 days. Change city opens a sheet with
 * the city suggestions; the new city is saved and its weather loaded.
 */
public class CityWeatherActivity extends BaseActivity {

    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

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
        labelStat(R.id.weather_wind, R.drawable.ic_wind, R.string.weather_wind);
        labelStat(R.id.weather_humidity, R.drawable.ic_drop, R.string.weather_humidity);
        labelStat(R.id.weather_rain, R.drawable.ic_umbrella, R.string.weather_rain_chance);
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
        findViewById(R.id.weather_unavailable).setVisibility(View.GONE);
        showForecast(false);
        api.forecast().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable ForecastDto forecast) {
                findViewById(R.id.weather_loading).setVisibility(View.GONE);
                if (forecast == null) {
                    findViewById(R.id.weather_unavailable).setVisibility(View.VISIBLE);
                    return;
                }
                show(forecast);
                showForecast(true);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.weather_loading).setVisibility(View.GONE);
                findViewById(R.id.weather_unavailable).setVisibility(View.VISIBLE);
            }
        });
    }

    /** Shows or hides everything that comes from the forecast: the weather on the card and the sections under it. */
    private void showForecast(boolean shown) {
        int visibility = shown ? View.VISIBLE : View.GONE;
        for (int id : new int[] {R.id.weather_now, R.id.weather_tips_card, R.id.weather_today_header,
                R.id.weather_hours_scroll, R.id.weather_week_title, R.id.weather_days}) {
            findViewById(id).setVisibility(visibility);
        }
    }

    private void show(ForecastDto forecast) {
        ((ImageView) findViewById(R.id.weather_icon)).setImageResource(picture(forecast.sky, forecast.day));
        ((TextView) findViewById(R.id.weather_temperature))
                .setText(getString(R.string.city_weather_now, forecast.temperature));
        ((TextView) findViewById(R.id.weather_feel))
                .setText(getString(R.string.city_weather_feel, capitalized(forecast.sky), forecast.feel));
        statValue(R.id.weather_wind, getString(R.string.weather_wind_value, forecast.wind));
        statValue(R.id.weather_humidity, getString(R.string.weather_percent, forecast.humidity));
        statValue(R.id.weather_rain, getString(R.string.weather_percent, forecast.rainChance));

        LayoutInflater inflater = LayoutInflater.from(this);
        ViewGroup tips = findViewById(R.id.weather_tips);
        tips.removeAllViews();
        for (WeatherAdvice.Tip tip : WeatherAdvice.forToday(this, forecast)) {
            View row = inflater.inflate(R.layout.item_weather_tip, tips, false);
            ((ImageView) row.findViewById(R.id.tip_icon)).setImageResource(tip.icon);
            ((TextView) row.findViewById(R.id.tip_text)).setText(tip.text);
            tips.addView(row);
        }

        ((TextView) findViewById(R.id.weather_high_low))
                .setText(getString(R.string.weather_high_low, forecast.high, forecast.low));
        ViewGroup hours = findViewById(R.id.weather_hours);
        hours.removeAllViews();
        List<ForecastDto.Hour> forecastHours = forecast.hours == null ? List.of() : forecast.hours;
        for (int i = 0; i < forecastHours.size(); i++) {
            hours.addView(hourView(inflater, hours, forecastHours.get(i), i == 0));
        }
        findViewById(R.id.weather_hours_scroll).scrollTo(0, 0);

        ViewGroup days = findViewById(R.id.weather_days);
        days.removeAllViews();
        List<ForecastDto.Day> forecastDays = forecast.days == null ? List.of() : forecast.days;
        for (int i = 0; i < forecastDays.size(); i++) {
            days.addView(dayView(inflater, days, forecastDays.get(i), i == 0));
        }
    }

    /** One capsule of the hours strip. The current hour is "Now", in the mocha of the card above. */
    private View hourView(LayoutInflater inflater, ViewGroup parent, ForecastDto.Hour hour, boolean now) {
        View view = inflater.inflate(R.layout.item_weather_hour, parent, false);
        TextView temperature = view.findViewById(R.id.hour_temperature);
        TextView rain = view.findViewById(R.id.hour_rain);
        TextView time = view.findViewById(R.id.hour_time);
        temperature.setText(getString(R.string.weather_degrees, hour.temperature));
        ((ImageView) view.findViewById(R.id.hour_icon)).setImageResource(picture(hour.sky, hour.day));
        rain.setText(getString(R.string.weather_percent, hour.rainChance));
        rain.setVisibility(hour.rainChance >= WeatherAdvice.RAIN_MAYBE ? View.VISIBLE : View.INVISIBLE);
        time.setText(now ? getString(R.string.weather_now_hour) : WeatherAdvice.clock(hour.time));
        if (now) {
            view.setBackgroundResource(R.drawable.bg_weather_hour_now);
            temperature.setTextColor(ContextCompat.getColor(this, R.color.on_dark));
            rain.setTextColor(ContextCompat.getColor(this, R.color.mocha_light));
            time.setTextColor(ContextCompat.getColor(this, R.color.mocha_light));
        }
        return view;
    }

    /** One row of the week ahead: "Today", then the days by their short names. */
    private View dayView(LayoutInflater inflater, ViewGroup parent, ForecastDto.Day day, boolean today) {
        View view = inflater.inflate(R.layout.item_weather_day, parent, false);
        ((TextView) view.findViewById(R.id.day_name)).setText(today ? getString(R.string.weather_today) : weekday(day.date));
        ((ImageView) view.findViewById(R.id.day_icon)).setImageResource(picture(day.sky, true));
        ((TextView) view.findViewById(R.id.day_sky)).setText(capitalized(day.sky));
        TextView rain = view.findViewById(R.id.day_rain);
        rain.setText(getString(R.string.weather_percent, day.rainChance));
        rain.setVisibility(day.rainChance >= WeatherAdvice.RAIN_MAYBE ? View.VISIBLE : View.GONE);
        ((TextView) view.findViewById(R.id.day_high)).setText(getString(R.string.weather_degrees, day.high));
        ((TextView) view.findViewById(R.id.day_low)).setText(getString(R.string.weather_degrees, day.low));
        return view;
    }

    /** Gives one of the three values on the card its icon and its name; the value comes with the weather. */
    private void labelStat(@IdRes int stat, @DrawableRes int icon, @StringRes int label) {
        View view = findViewById(stat);
        ((ImageView) view.findViewById(R.id.stat_icon)).setImageResource(icon);
        ((TextView) view.findViewById(R.id.stat_label)).setText(label);
    }

    private void statValue(@IdRes int stat, String value) {
        ((TextView) findViewById(stat).findViewById(R.id.stat_value)).setText(value);
    }

    /** The picture for a sky, by day or by night (the moon instead of the sun). */
    @DrawableRes
    private static int picture(@Nullable String sky, boolean day) {
        if (sky == null) return R.drawable.weather_cloud;
        return switch (sky) {
            case "clear" -> day ? R.drawable.weather_sun : R.drawable.weather_moon;
            case "partly cloudy" -> day ? R.drawable.weather_partly : R.drawable.weather_partly_night;
            case "foggy" -> R.drawable.weather_fog;
            case "drizzle", "rainy" -> R.drawable.weather_rain;
            case "snowy" -> R.drawable.weather_snow;
            case "stormy" -> R.drawable.weather_storm;
            default -> R.drawable.weather_cloud;
        };
    }

    /** "2026-10-22" → "Thu". */
    private static String weekday(@Nullable String date) {
        try {
            return date == null ? "" : LocalDate.parse(date).format(WEEKDAY);
        } catch (DateTimeParseException e) {
            return "";
        }
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
