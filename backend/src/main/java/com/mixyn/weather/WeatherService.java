package com.mixyn.weather;

import java.time.Duration;
import java.time.Instant;
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
 * Current weather for the user's city, from the free Open-Meteo forecast API (no key), using the
 * coordinates saved at sign-up. Answers are remembered for 15 minutes per place, and any failure
 * simply means "no weather": the canvas hides its chip and the stylist works without it.
 */
@Service
public class WeatherService {

    /** What the app shows ("4° · cold") and what the stylist is told ("4°C, overcast, cold"). */
    public record Weather(int temperature, String feel, String sky, String city) {
    }

    // Shape of the Open-Meteo response; only the fields we use.
    record Current(double temperature_2m, double apparent_temperature, int weather_code) {
    }

    record Forecast(Current current) {
    }

    private record Cached(Current now, Instant at) {
    }

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final Duration FRESH_FOR = Duration.ofMinutes(15);

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
        if (user == null || user.getLatitude() == null || user.getLongitude() == null) {
            return Optional.empty();
        }
        String place = user.getLatitude() + "," + user.getLongitude();
        Cached cached = cache.get(place);
        if (cached == null || !cached.at().plus(FRESH_FOR).isAfter(Instant.now())) {
            Current now = lookUp(user.getLatitude(), user.getLongitude());
            if (now == null) {
                return Optional.empty();
            }
            cached = new Cached(now, Instant.now());
            cache.put(place, cached);
        }
        Current now = cached.now();
        return Optional.of(new Weather((int) Math.round(now.temperature_2m()),
                feel(now.apparent_temperature()), sky(now.weather_code()), user.getCity()));
    }

    /** The weather at this place right now, or null if Open-Meteo can't be reached. */
    private Current lookUp(double latitude, double longitude) {
        try {
            Forecast forecast = client.get()
                    .uri(uri -> uri.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", "temperature_2m,apparent_temperature,weather_code")
                            .build())
                    .retrieve()
                    .body(Forecast.class);
            return forecast == null ? null : forecast.current();
        } catch (Exception e) {
            log.warn("Weather lookup failed: {}", e.toString());
            return null;
        }
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
        if (code <= 2) return "partly cloudy";
        if (code == 3) return "cloudy";
        if (code == 45 || code == 48) return "foggy";
        if (code >= 51 && code <= 57) return "drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "rainy";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snowy";
        if (code >= 95) return "stormy";
        return "cloudy";
    }
}
