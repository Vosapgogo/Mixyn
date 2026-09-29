package com.mixyn.app.api.dto;

public class RegisterRequest {
    public final String name;
    public final String email;
    public final String password;
    public final String city;
    public final String country;
    public final double latitude;
    public final double longitude;

    public RegisterRequest(String name, String email, String password, CityDto city) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.city = city.name;
        this.country = city.country;
        this.latitude = city.latitude;
        this.longitude = city.longitude;
    }
}
