package com.mixyn.user;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemRepository;
import com.mixyn.outfit.Outfit;
import com.mixyn.outfit.OutfitRepository;
import com.mixyn.outfit.WearRepository;
import com.mixyn.storage.ImageStorage;
import com.mixyn.user.UserDtos.PasswordChange;
import com.mixyn.user.UserDtos.ProfileUpdate;
import com.mixyn.user.UserDtos.UserResponse;

/**
 * The user's own account: the profile (name, username, city), the password, the profile photo, and
 * deleting the account with everything in it.
 */
@Service
public class ProfileService {

    /** A username is 3 to 30 lower-case letters, digits, dots and underscores ("koko", "mira.k"). */
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._]{3,30}");

    private final UserRepository users;
    private final SessionRepository sessions;
    private final ItemRepository items;
    private final OutfitRepository outfits;
    private final WearRepository wears;
    private final ImageStorage storage;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public ProfileService(UserRepository users, SessionRepository sessions, ItemRepository items,
            OutfitRepository outfits, WearRepository wears, ImageStorage storage) {
        this.users = users;
        this.sessions = sessions;
        this.items = items;
        this.outfits = outfits;
        this.wears = wears;
        this.storage = storage;
    }

    @Transactional
    public UserResponse update(Long userId, ProfileUpdate update) {
        User user = user(userId);
        String username = normalizeUsername(update.username());
        if (username != null && !USERNAME.matcher(username).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_username",
                    "A username has 3 to 30 letters, digits, dots or underscores");
        }
        if (username != null && users.existsByUsernameAndIdNot(username, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "username_taken", "This username is taken");
        }
        user.setName(update.name());
        user.setUsername(username);
        user.setCity(update.city(), update.country(), update.latitude(), update.longitude());
        return UserResponse.from(users.save(user));
    }

    /** A new password needs the current one. Every other session of the user ends: other phones log in again. */
    @Transactional
    public void changePassword(Long userId, String token, PasswordChange change) {
        User user = user(userId);
        boolean current = !AuthService.tooLong(change.currentPassword())
                && passwordEncoder.matches(change.currentPassword(), user.getPasswordHash());
        if (!current) {
            throw new ApiException(HttpStatus.FORBIDDEN, "wrong_password", "Your current password is incorrect");
        }
        if (AuthService.tooLong(change.newPassword())) {
            throw AuthService.passwordTooLong();
        }
        user.setPasswordHash(passwordEncoder.encode(change.newPassword()));
        users.save(user);
        sessions.deleteByUserIdAndTokenNot(userId, token);
    }

    /** The new profile photo replaces the old one, whose file goes. */
    @Transactional
    public UserResponse setAvatar(Long userId, byte[] photo) {
        User user = user(userId);
        String old = user.getAvatarFile();
        user.setAvatarFile(storage.save(storage.squareAvatar(photo)));
        UserResponse saved = UserResponse.from(users.save(user));
        if (old != null) {
            storage.delete(old);
        }
        return saved;
    }

    /**
     * The account and everything in it: the calendar, the outfits with their pictures, the clothes with
     * their photos, the profile photo and every session. Nothing of the user is left.
     */
    @Transactional
    public void deleteAccount(Long userId) {
        User user = user(userId);
        wears.deleteAll(wears.findByUserIdOrderByWornOn(userId));
        for (Outfit outfit : outfits.findByUserIdOrderByCreatedAtDesc(userId)) {
            outfits.delete(outfit);
            storage.delete(outfit.getImageFile());
        }
        for (Item item : items.findByUserIdOrderByCreatedAtDesc(userId)) {
            items.delete(item);
            storage.delete(item.getImageFile());
        }
        if (user.getAvatarFile() != null) {
            storage.delete(user.getAvatarFile());
        }
        sessions.deleteByUserId(userId);
        users.delete(user);
    }

    /** "@Koko " → "koko"; an empty username is none. */
    static String normalizeUsername(String typed) {
        if (typed == null) {
            return null;
        }
        String username = typed.trim();
        if (username.startsWith("@")) {
            username = username.substring(1);
        }
        username = username.toLowerCase(Locale.ROOT);
        return username.isEmpty() ? null : username;
    }

    private User user(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "User not found"));
    }
}
