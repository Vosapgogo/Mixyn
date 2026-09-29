package com.mixyn.app.api.dto;

import androidx.annotation.NonNull;

public class CityDto {
    public String name;
    public String region;
    public String country;
    public double latitude;
    public double longitude;

    /** What the dropdown shows: "Lviv, Lviv Oblast, Ukraine". */
    @NonNull
    @Override
    public String toString() {
        StringBuilder label = new StringBuilder(name);
        if (region != null && !region.equals(name)) label.append(", ").append(region);
        if (country != null) label.append(", ").append(country);
        return label.toString();
    }
}
