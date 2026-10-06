package com.mixyn.weather;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import com.mixyn.common.ApiException;
import com.mixyn.user.AuthInterceptor;
import com.mixyn.weather.WeatherService.Weather;

@RestController
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    /** Weather in the logged-in user's city, e.g. {"temperature": 4, "feel": "cold", "sky": "cloudy", "city": "Kyiv"}. */
    @GetMapping("/api/me/weather")
    public Weather weather(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        return weatherService.forUser(userId).orElseThrow(() ->
                new ApiException(HttpStatus.BAD_GATEWAY, "weather_unavailable", "Can't load the weather right now"));
    }
}
