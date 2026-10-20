package com.mixyn.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** Always stored trimmed and lower-case, so login is case-insensitive. */
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    /** BCrypt hash; the plain password is never stored. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 80)
    private String country;

    /** Kept for weather-aware outfits later. */
    private Double latitude;

    private Double longitude;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** The name friends will find the user by ("@koko"): lower-case, unique, optional. */
    @Column(unique = true, length = 30)
    private String username;

    /** The profile photo's file in the image folder, or null for none. */
    @Column(name = "avatar_file")
    private String avatarFile;

    protected User() {
    }

    public User(String name, String email, String passwordHash,
            String city, String country, Double latitude, Double longitude) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.city = city;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    /** A city chosen from the suggestions, with where it is (for the weather). */
    public void setCity(String city, String country, Double latitude, Double longitude) {
        this.city = city;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAvatarFile() {
        return avatarFile;
    }

    public void setAvatarFile(String avatarFile) {
        this.avatarFile = avatarFile;
    }
}
