package com.mixyn.app.api.dto;

/** Weather in the user's city right now. */
public class WeatherDto {
    public int temperature; // °C
    public String feel;     // "cold", "cool", "mild", "warm"…
    public String sky;      // "cloudy", "rainy"…
    public String city;
}
