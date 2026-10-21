package com.mixyn.user;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mixyn.common.ApiException;
import com.mixyn.user.UserDtos.AuthResponse;
import com.mixyn.user.UserDtos.RegisterRequest;
import com.mixyn.user.UserDtos.UserResponse;

@Service
public class AuthService {

    private final UserRepository users;
    private final SessionRepository sessions;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, SessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    /**
     * BCrypt reads at most 72 bytes of a password. A Latin letter is one byte but a Cyrillic one is two,
     * so a password of 72 characters can be too long; the encoder then refuses it with an error.
     */
    static final int MAX_PASSWORD_BYTES = 72;

    static boolean tooLong(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
    }

    static ApiException passwordTooLong() {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed",
                "This password is too long. Use a shorter one.");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (tooLong(request.password())) {
            throw passwordTooLong();
        }
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken",
                    "An account with this email already exists. Try logging in.");
        }
        User user = users.save(new User(request.name(), email, passwordEncoder.encode(request.password()),
                request.city(), request.country(), request.latitude(), request.longitude()));
        return new AuthResponse(UserResponse.from(user), createSession(user));
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        // Same error for an unknown email and a wrong password, so the API doesn't reveal which emails exist.
        // A password too long to have been saved can't be anyone's.
        User user = users.findByEmail(normalizeEmail(email))
                .filter(u -> !tooLong(password) && passwordEncoder.matches(password, u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials",
                        "Email or password is incorrect"));
        return new AuthResponse(UserResponse.from(user), createSession(user));
    }

    @Transactional
    public void logout(String token) {
        sessions.deleteById(token);
    }

    /** The user id behind a session token, if the token is still valid. */
    public Optional<Long> userIdForToken(String token) {
        return sessions.findById(token).map(Session::getUserId);
    }

    public UserResponse getUser(Long userId) {
        return users.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found"));
    }

    private String createSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.save(new Session(token, user.getId()));
        return token;
    }

    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
