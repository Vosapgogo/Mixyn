package com.mixyn.app.ui.profile;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.mixyn.app.R;
import com.mixyn.app.api.dto.ForecastDto;

import java.util.ArrayList;
import java.util.List;

/**
 * Tips for the day on the weather page, from today's forecast: what to wear for how it feels, then
 * whether to take an umbrella or wear a raincoat (and from which hour the rain is likely), snow, strong
 * wind, strong sun, a big difference between the morning and the afternoon, a hot day. Only the hours
 * still to come today count: rain this morning is no reason to take an umbrella in the evening.
 */
final class WeatherAdvice {

    /** One tip: an icon for what it is about, and the sentence. */
    static final class Tip {
        @DrawableRes
        final int icon;
        final String text;

        Tip(@DrawableRes int icon, String text) {
            this.icon = icon;
            this.text = text;
        }
    }

    /** From this chance of rain in an hour (%), rain is likely: time for an umbrella or a raincoat. */
    static final int RAIN_LIKELY = 50;
    /** From this chance over the rest of the day, a small umbrella in the bag. */
    static final int RAIN_MAYBE = 30;
    /** From this wind (km/h) an umbrella turns inside out and a wide hat blows off. */
    static final int WINDY = 30;
    /** From this UV index the sun calls for sunglasses and sunscreen. */
    static final double STRONG_SUN = 6;
    /** This much between today's low and high (°) calls for layers. */
    static final int BIG_SWING = 8;
    /** From this high (°) the day is hot. */
    static final int HOT = 28;

    private WeatherAdvice() {
    }

    static List<Tip> forToday(Context context, ForecastDto forecast) {
        List<Tip> tips = new ArrayList<>();
        tips.add(new Tip(R.drawable.ic_hanger, context.getString(whatToWear(forecast.feel))));

        boolean windy = forecast.windMax >= WINDY;
        boolean windSaid = false;
        ForecastDto.Hour wet = firstWetHour(forecast.hours);
        if (wet != null) {
            String when = wet == forecast.hours.get(0) ? context.getString(R.string.weather_when_now)
                    : context.getString(R.string.weather_when_from, clock(wet.time));
            if ("snowy".equals(wet.sky)) {
                tips.add(new Tip(R.drawable.ic_snowflake, context.getString(R.string.weather_tip_snow, when)));
            } else if ("stormy".equals(wet.sky)) {
                tips.add(new Tip(R.drawable.ic_umbrella, context.getString(R.string.weather_tip_storm, when)));
            } else {
                // In strong wind an umbrella is no help: the rain tip says so itself.
                windSaid = windy;
                tips.add(new Tip(R.drawable.ic_umbrella, context.getString(
                        windy ? R.string.weather_tip_rain_wind : R.string.weather_tip_rain, when)));
            }
        } else if (forecast.rainChance >= RAIN_MAYBE) {
            tips.add(new Tip(R.drawable.ic_umbrella,
                    context.getString(R.string.weather_tip_rain_maybe, forecast.rainChance)));
        } else {
            tips.add(new Tip(R.drawable.ic_umbrella, context.getString(R.string.weather_tip_dry)));
        }

        if (windy && !windSaid) {
            tips.add(new Tip(R.drawable.ic_wind, context.getString(R.string.weather_tip_wind, forecast.windMax)));
        }
        if (forecast.uvIndex >= STRONG_SUN) {
            tips.add(new Tip(R.drawable.ic_sun,
                    context.getString(R.string.weather_tip_sun, (int) Math.round(forecast.uvIndex))));
        }
        if (forecast.high - forecast.low >= BIG_SWING) {
            tips.add(new Tip(R.drawable.ic_jacket,
                    context.getString(R.string.weather_tip_layers, forecast.low, forecast.high)));
        }
        if (forecast.high >= HOT) {
            tips.add(new Tip(R.drawable.ic_drop, context.getString(R.string.weather_tip_hot)));
        }
        return tips;
    }

    /** The first hour still to come today in which rain (or snow) is likely; null when today stays dry. */
    @Nullable
    static ForecastDto.Hour firstWetHour(@Nullable List<ForecastDto.Hour> hours) {
        if (hours == null || hours.isEmpty() || hours.get(0).time == null || hours.get(0).time.length() < 10) {
            return null;
        }
        String today = hours.get(0).time.substring(0, 10);
        for (ForecastDto.Hour hour : hours) {
            if (hour.time == null || !hour.time.startsWith(today)) break; // tomorrow already
            if (hour.rainChance >= RAIN_LIKELY) return hour;
        }
        return null;
    }

    /** The time of day of a forecast hour: "2026-10-21T15:00" → "15:00". */
    static String clock(@Nullable String time) {
        return time != null && time.length() >= 16 ? time.substring(11, 16) : "";
    }

    /** A line on what to wear, by how the weather feels (the backend's "feel"). */
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
}
