package com.mixyn.weather;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.mixyn.user.User;
import com.mixyn.user.UserRepository;

/**
 * Weather for the user's city, from the free Open-Meteo forecast API (no key), using the coordinates
 * saved at sign-up. One call brings the weather right now, every hour and the next seven days; answers
 * are remembered for 15 minutes per place. Any failure simply means "no weather": the canvas hides its
 * chip, the weather page says so, and the stylist works without it.
 */
@Service
public class WeatherService {

    /** What the app shows ("4° · cold") and what the stylist is told ("4°C, overcast, cold"). */
    public record Weather(int temperature, String feel, String sky, String city) {
    }

    /**
     * Today in detail and the week ahead, for the City for weather page. Wind in km/h; high and low are
     * today's; rainChance (the chance of rain or snow, %) is for the rest of today; windMax and uvIndex
     * are today's highest.
     */
    public record Outlook(String city, int temperature, String feel, String sky, boolean day, int wind,
            int humidity, int high, int low, int rainChance, int windMax, double uvIndex, List<Hour> hours,
            List<Day> days) {
    }

    /** One hour, from the current one on: local time ("2026-10-21T15:00"), °C, sky, daytime or not, % of rain. */
    public record Hour(String time, int temperature, String sky, boolean day, int rainChance) {
    }

    /** One day, today first: date ("2026-10-21"), highest and lowest °C, sky, % of rain. */
    public record Day(String date, int high, int low, String sky, int rainChance) {
    }

    // Shape of the Open-Meteo response; only the fields we use. Any value in it may be null.
    record Current(String time, Double temperature_2m, Double apparent_temperature, Integer weather_code,
            Double wind_speed_10m, Integer relative_humidity_2m, Integer is_day) {
    }

    record Hourly(List<String> time, List<Double> temperature_2m, List<Integer> weather_code,
            List<Integer> precipitation_probability, List<Integer> is_day) {
    }

    record Daily(List<String> time, List<Integer> weather_code, List<Double> temperature_2m_max,
            List<Double> temperature_2m_min, List<Integer> precipitation_probability_max,
            List<Double> wind_speed_10m_max, List<Double> uv_index_max) {
    }

    record Forecast(Current current, Hourly hourly, Daily daily) {
    }

    private record Cached(Forecast forecast, Instant at) {
    }

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final Duration FRESH_FOR = Duration.ofMinutes(15);
    /** How many hours the page shows, the current one first. */
    static final int HOURS = 24;
    private static final int DAYS = 7;

    private final RestClient client = RestClient.create("https://api.open-meteo.com");
    private final UserRepository users;
    /**
     * Recent answers by place ("50.45,30.52"), not by user: a user who changes their city gets the new
     * city's weather at once, instead of the old one for up to 15 minutes.
     */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public WeatherService(UserRepository users) {
        this.users = users;
    }

    public Optional<Weather> forUser(Long userId) {
        User user = users.findById(userId).orElse(null);
        Forecast forecast = forecastFor(user);
        if (forecast == null) {
            return Optional.empty();
        }
        Current now = forecast.current();
        return Optional.of(new Weather(round(now.temperature_2m()), feel(value(now.apparent_temperature())),
                sky(code(now.weather_code())), user.getCity()));
    }

    /** Today in detail and the week ahead, or empty when the user has no city or Open-Meteo can't be reached. */
    public Optional<Outlook> outlookForUser(Long userId) {
        User user = users.findById(userId).orElse(null);
        Forecast forecast = forecastFor(user);
        return forecast == null ? Optional.empty() : Optional.of(outlook(forecast, user.getCity()));
    }

    /** The forecast for the user's place, fresh or remembered; null without coordinates or an answer. */
    private Forecast forecastFor(User user) {
        if (user == null || user.getLatitude() == null || user.getLongitude() == null) {
            return null;
        }
        String place = user.getLatitude() + "," + user.getLongitude();
        Cached cached = cache.get(place);
        if (cached == null || !cached.at().plus(FRESH_FOR).isAfter(Instant.now())) {
            Forecast forecast = lookUp(user.getLatitude(), user.getLongitude());
            if (forecast == null) {
                return null;
            }
            cached = new Cached(forecast, Instant.now());
            cache.put(place, cached);
        }
        return cached.forecast();
    }

    /**
     * The weather at this place: right now, every hour and every day of the coming week, in the place's
     * own time zone. Null if Open-Meteo can't be reached or answers without the weather right now.
     */
    private Forecast lookUp(double latitude, double longitude) {
        try {
            Forecast forecast = client.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,apparent_temperature,weather_code,"
                                    + "wind_speed_10m,relative_humidity_2m,is_day")
                            .queryParam("hourly", "temperature_2m,weather_code,precipitation_probability,is_day")
                            .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,"
                                    + "precipitation_probability_max,wind_speed_10m_max,uv_index_max")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", DAYS)
                            .build())
                    .retrieve()
                    .body(Forecast.class);
            return forecast == null || forecast.current() == null ? null : forecast;
        } catch (Exception e) {
            log.warn("Weather lookup failed: {}", e.toString());
            return null;
        }
    }

    /**
     * What the weather page shows, from one forecast: the hours from the current one on, and for the
     * rest of today the highest chance of rain. Hours that already passed don't count: rain this morning
     * is no reason to take an umbrella in the evening.
     */
    static Outlook outlook(Forecast forecast, String city) {
        Current now = forecast.current();
        Hourly hourly = forecast.hourly();
        Daily daily = forecast.daily();
        List<String> times = hourly == null || hourly.time() == null ? List.of() : hourly.time();
        // "2026-10-21T15:45" → the hour it falls in, "2026-10-21T15:00"
        String thisHour = now.time() == null || now.time().length() < 13 ? "" : now.time().substring(0, 13) + ":00";
        int first = Math.max(0, times.indexOf(thisHour));
        String today = thisHour.isEmpty() ? "" : thisHour.substring(0, 10);

        List<Hour> hours = new ArrayList<>();
        int rainChance = 0;
        for (int i = first; i < times.size(); i++) {
            int rain = round(at(hourly.precipitation_probability(), i));
            if (times.get(i).startsWith(today)) rainChance = Math.max(rainChance, rain);
            if (hours.size() < HOURS) {
                hours.add(new Hour(times.get(i), round(at(hourly.temperature_2m(), i)),
                        sky(code(at(hourly.weather_code(), i))), value(at(hourly.is_day(), i)) != 0, rain));
            }
        }

        List<Day> days = new ArrayList<>();
        List<String> dates = daily == null || daily.time() == null ? List.of() : daily.time();
        for (int i = 0; i < dates.size(); i++) {
            days.add(new Day(dates.get(i), round(at(daily.temperature_2m_max(), i)),
                    round(at(daily.temperature_2m_min(), i)), sky(code(at(daily.weather_code(), i))),
                    round(at(daily.precipitation_probability_max(), i))));
        }
        int temperature = round(now.temperature_2m());
        // Today's high and low cover the hours still to come, so they never leave out the temperature now.
        int high = Math.max(temperature, days.isEmpty() ? temperature : days.get(0).high());
        int low = Math.min(temperature, days.isEmpty() ? temperature : days.get(0).low());
        int windMax = Math.max(round(now.wind_speed_10m()), daily == null ? 0 : round(at(daily.wind_speed_10m_max(), 0)));
        double uvIndex = daily == null ? 0 : value(at(daily.uv_index_max(), 0));

        return new Outlook(city, temperature, feel(value(now.apparent_temperature())), sky(code(now.weather_code())),
                value(now.is_day()) == 1, round(now.wind_speed_10m()),
                round(now.relative_humidity_2m()), high, low, rainChance, windMax, uvIndex, hours, days);
    }

    /** The value at this place in an Open-Meteo list, or null when the list is shorter or has a gap there. */
    private static Number at(List<? extends Number> values, int index) {
        return values == null || index >= values.size() ? null : values.get(index);
    }

    private static double value(Number number) {
        return number == null ? 0 : number.doubleValue();
    }

    private static int round(Number number) {
        return (int) Math.round(value(number));
    }

    private static int code(Number number) {
        return number == null ? -1 : number.intValue();
    }

    /** One word for how it feels outside, from the "feels like" temperature in °C. */
    static String feel(double feelsLike) {
        if (feelsLike < 0) return "freezing";
        if (feelsLike < 8) return "cold";
        if (feelsLike < 15) return "cool";
        if (feelsLike < 22) return "mild";
        if (feelsLike < 28) return "warm";
        return "hot";
    }

    /** A short description of the sky from the WMO weather code Open-Meteo returns. */
    static String sky(int code) {
        if (code == 0) return "clear";
        if (code == 1 || code == 2) return "partly cloudy";
        if (code == 3) return "cloudy";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "rainy";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snowy";
        if (code >= 95) return "stormy";
        return "cloudy";
    }
}
