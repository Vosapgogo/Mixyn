package com.mixyn.user;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mixyn.user.UserDtos.PasswordChange;
import com.mixyn.user.UserDtos.ProfileUpdate;
import com.mixyn.user.UserDtos.UserResponse;

import jakarta.validation.Valid;

/** The signed-in user's own account (GET /api/me is in AuthController). */
@RestController
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @PutMapping("/api/me")
    public UserResponse update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
            @Valid @RequestBody ProfileUpdate update) {
        return profiles.update(userId, update);
    }

    @PutMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
            @RequestAttribute(AuthInterceptor.TOKEN) String token, @Valid @RequestBody PasswordChange change) {
        profiles.changePassword(userId, token, change);
    }

    /** A multipart request with one part, "photo" (JPEG or PNG). */
    @PostMapping("/api/me/avatar")
    public UserResponse setAvatar(@RequestAttribute(AuthInterceptor.USER_ID) Long userId,
            @RequestParam("photo") MultipartFile photo) throws IOException {
        return profiles.setAvatar(userId, photo.getBytes());
    }

    @DeleteMapping("/api/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@RequestAttribute(AuthInterceptor.USER_ID) Long userId) {
        profiles.deleteAccount(userId);
    }
}
