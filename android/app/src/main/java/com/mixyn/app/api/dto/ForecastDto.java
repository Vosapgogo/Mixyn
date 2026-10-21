package com.mixyn.app.api.dto;

import java.util.List;

/**
 * Today in detail and the week ahead in the user's city (the City for weather page). High and low are
 * today's; rainChance (the chance of rain or snow, %) is for the rest of today; wind is in km/h.
 */
public class ForecastDto {
    public String city;
    public int temperature; // °C
    public String feel;     // "cold", "cool", "mild"…
    public String sky;      // "cloudy", "rainy"…
    public boolean day;     // false at night: a moon instead of the sun
    public int wind;
    public int humidity;    // %
    public int high;
    public int low;
    public int rainChance;
    public int windMax;     // today's strongest wind
    public double uvIndex;  // today's highest
    public List<Hour> hours; // the current hour first
    public List<Day> days;   // today first

    public static class Hour {
        public String time; // local time, "2026-10-21T15:00"
        public int temperature;
        public String sky;
        public boolean day;
        public int rainChance;
    }

    public static class Day {
        public String date; // "2026-10-21"
        public int high;
        public int low;
        public String sky;
        public int rainChance;
    }
}
