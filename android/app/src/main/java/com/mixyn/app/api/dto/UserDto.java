package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

public class UserDto {
    public long id;
    public String name;
    public String email;
    public String city;
    public String country;
    @Nullable
    public Double latitude;
    @Nullable
    public Double longitude;
    /** The name friends will find the user by, without the "@"; null until the user sets one. */
    @Nullable
    public String username;
    /** Path on the backend, like an item photo; null without a profile photo. */
    @Nullable
    public String avatarUrl;
}
