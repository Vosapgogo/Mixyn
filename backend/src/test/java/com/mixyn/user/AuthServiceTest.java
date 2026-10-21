package com.mixyn.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mixyn.common.ApiException;
import com.mixyn.user.UserDtos.RegisterRequest;

/**
 * Tests the password length rule of sign-up and log-in: BCrypt reads at most 72 bytes, so a password of
 * 72 Cyrillic letters (144 bytes) must be answered with a clear error, not a server error. The
 * repositories are stand-ins; no Spring, no database.
 */
class AuthServiceTest {

    /** 72 characters, but 144 bytes. */
    private static final String LONG_CYRILLIC = "ж".repeat(72);

    private final UserRepository users = mock(UserRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final AuthService auth = new AuthService(users, sessions);

    @Test
    void countsBytesNotCharacters() {
        assertFalse(AuthService.tooLong("a".repeat(72)));
        assertFalse(AuthService.tooLong("ж".repeat(36)));
        assertTrue(AuthService.tooLong("ж".repeat(37)));
    }

    @Test
    void signUpRefusesAPasswordTooLongToSave() {
        ApiException refused = assertThrows(ApiException.class, () -> auth.register(new RegisterRequest(
                "Koko", "koko@example.com", LONG_CYRILLIC, "Kyiv", "Ukraine", 50.45, 30.52)));

        assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        assertEquals("validation_failed", refused.getCode());
        verify(users, never()).save(any());
    }

    @Test
    void aPasswordTooLongToSaveLogsNobodyIn() {
        User user = new User("Koko", "koko@example.com", new BCryptPasswordEncoder().encode("old-password"),
                "Kyiv", "Ukraine", 50.45, 30.52);
        when(users.findByEmail("koko@example.com")).thenReturn(Optional.of(user));

        ApiException refused = assertThrows(ApiException.class, () -> auth.login("koko@example.com", LONG_CYRILLIC));

        assertEquals("invalid_credentials", refused.getCode());
    }
}
