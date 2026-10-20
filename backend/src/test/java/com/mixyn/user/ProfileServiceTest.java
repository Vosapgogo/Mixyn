package com.mixyn.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemRepository;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.Outfit;
import com.mixyn.outfit.OutfitRepository;
import com.mixyn.outfit.Wear;
import com.mixyn.outfit.WearRepository;
import com.mixyn.storage.ImageStorage;
import com.mixyn.user.UserDtos.PasswordChange;
import com.mixyn.user.UserDtos.ProfileUpdate;

/**
 * Tests the rules of the user's own account: a username is someone's only once and has a fixed form,
 * a new password needs the current one and ends the other sessions, and deleting the account leaves
 * nothing of the user. The repositories are stand-ins; no Spring, no database, no network.
 */
class ProfileServiceTest {

    private static final long USER = 7L;

    private final UserRepository users = mock(UserRepository.class);
    private final SessionRepository sessions = mock(SessionRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final OutfitRepository outfits = mock(OutfitRepository.class);
    private final WearRepository wears = mock(WearRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);
    private final ProfileService profiles = new ProfileService(users, sessions, items, outfits, wears, storage);
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Koko", "koko@example.com", new BCryptPasswordEncoder().encode("old-password"),
                "Kyiv", "Ukraine", 50.45, 30.52);
        setId(User.class, user, USER);
        when(users.findById(USER)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void savesTheProfileWithATidyUsername() {
        var saved = profiles.update(USER, new ProfileUpdate("Koko M", " @Koko.Style ", "Lviv", "Ukraine", 49.84, 24.03));

        assertEquals("koko.style", saved.username());
        assertEquals("Koko M", saved.name());
        assertEquals("Lviv", saved.city());
    }

    @Test
    void anEmptyUsernameIsNone() {
        assertNull(profiles.update(USER, new ProfileUpdate("Koko", "  ", "Kyiv", null, 50.45, 30.52)).username());
    }

    @Test
    void refusesAUsernameSomeoneElseHasOrOfTheWrongForm() {
        when(users.existsByUsernameAndIdNot("mira", USER)).thenReturn(true);

        ApiException taken = assertThrows(ApiException.class,
                () -> profiles.update(USER, new ProfileUpdate("Koko", "mira", "Kyiv", null, 50.45, 30.52)));
        assertEquals("username_taken", taken.getCode());
        for (String bad : List.of("ko", "koko style", "кокo", "a".repeat(31))) {
            ApiException refused = assertThrows(ApiException.class,
                    () -> profiles.update(USER, new ProfileUpdate("Koko", bad, "Kyiv", null, 50.45, 30.52)));
            assertEquals("invalid_username", refused.getCode(), bad);
        }
    }

    @Test
    void aNewPasswordNeedsTheCurrentOneAndEndsTheOtherSessions() {
        ApiException wrong = assertThrows(ApiException.class,
                () -> profiles.changePassword(USER, "this-phone", new PasswordChange("guess", "new-password")));
        assertEquals("wrong_password", wrong.getCode());
        verify(sessions, never()).deleteByUserIdAndTokenNot(any(), any());

        profiles.changePassword(USER, "this-phone", new PasswordChange("old-password", "new-password"));

        assertTrue(new BCryptPasswordEncoder().matches("new-password", user.getPasswordHash()));
        verify(sessions).deleteByUserIdAndTokenNot(USER, "this-phone");
    }

    @Test
    void aNewProfilePhotoReplacesTheOldFile() {
        user.setAvatarFile("old.png");
        when(storage.squareAvatar(any())).thenReturn(new byte[] {1});
        when(storage.save(any())).thenReturn("new.png");

        var saved = profiles.setAvatar(USER, new byte[] {9});

        assertEquals("/images/new.png", saved.avatarUrl());
        verify(storage).delete("old.png");
    }

    @Test
    void deletingTheAccountLeavesNothingOfTheUser() {
        user.setAvatarFile("me.png");
        Item sweater = new Item(USER, "sweater.png", ItemType.SWEATER, ItemType.SWEATER.getCategory(), List.<ItemColor>of());
        Outfit outfit = new Outfit(USER, "look.png", List.of());
        List<Wear> days = List.of(new Wear(USER, 1L, java.time.LocalDate.of(2026, 10, 12)));
        when(items.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(sweater));
        when(outfits.findByUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(outfit));
        when(wears.findByUserIdOrderByWornOn(USER)).thenReturn(days);

        profiles.deleteAccount(USER);

        verify(wears).deleteAll(days);
        verify(outfits).delete(outfit);
        verify(items).delete(sweater);
        verify(storage).delete("look.png");
        verify(storage).delete("sweater.png");
        verify(storage).delete("me.png");
        verify(sessions).deleteByUserId(USER);
        verify(users).delete(user);
    }

    private static void setId(Class<?> type, Object entity, long id) {
        try {
            Field idField = type.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
