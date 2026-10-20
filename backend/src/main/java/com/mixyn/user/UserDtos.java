package com.mixyn.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 80) String country,
            @NotNull Double latitude,
            @NotNull Double longitude) {
        // Trim before validation: phone keyboards often add a trailing space.
        public RegisterRequest {
            name = trim(name);
            email = trim(email);
            city = trim(city);
            country = trim(country);
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
        public LoginRequest {
            email = trim(email);
        }
    }

    /** The user as the app sees them; avatarUrl is null without a profile photo. */
    public record UserResponse(Long id, String name, String email, String city, String country, Double latitude,
            Double longitude, String username, String avatarUrl) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCity(), user.getCountry(),
                    user.getLatitude(), user.getLongitude(), user.getUsername(),
                    user.getAvatarFile() == null ? null : "/images/" + user.getAvatarFile());
        }
    }

    /** Body of PUT /api/me: the whole profile as the edit screen has it. The city comes from the suggestions. */
    public record ProfileUpdate(
            @NotBlank @Size(max = 50) String name,
            @Size(max = 31) String username,
            @NotBlank @Size(max = 80) String city,
            @Size(max = 80) String country,
            @NotNull Double latitude,
            @NotNull Double longitude) {
        public ProfileUpdate {
            name = trim(name);
            username = trim(username);
            city = trim(city);
        }
    }

    /** Body of PUT /api/me/password. */
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record AuthResponse(UserResponse user, String token) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
