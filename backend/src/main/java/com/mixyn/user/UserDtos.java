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

    public record UserResponse(Long id, String name, String email, String city, String country) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getCity(),
                    user.getCountry());
        }
    }

    public record AuthResponse(UserResponse user, String token) {
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
