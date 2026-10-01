package com.mixyn.item;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One piece of clothing in a user's wardrobe. The photo itself is a file; this row points to it. */
@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "image_file", nullable = false)
    private String imageFile;

    /** Null when the AI couldn't tell; the user then chooses it on the item card. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    @Column(name = "color_name", length = 30)
    private String colorName;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(length = 60)
    private String name;

    /** Comma-separated, e.g. "Casual,Work". */
    private String tags;

    @Column(length = 20)
    private String season;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Item() {
    }

    public Item(Long userId, String imageFile, Category category, String colorName, String colorHex) {
        this.userId = userId;
        this.imageFile = imageFile;
        this.category = category;
        this.colorName = colorName;
        this.colorHex = colorHex;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getImageFile() {
        return imageFile;
    }

    public Category getCategory() {
        return category;
    }

    public String getColorName() {
        return colorName;
    }

    public String getColorHex() {
        return colorHex;
    }

    public String getName() {
        return name;
    }

    public String getTags() {
        return tags;
    }

    public String getSeason() {
        return season;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
