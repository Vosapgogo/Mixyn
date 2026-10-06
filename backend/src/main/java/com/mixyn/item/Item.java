package com.mixyn.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    /** What it is, from the fixed list (Blazer, Jeans…). Null when the AI couldn't tell; the user then chooses. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ItemType type;

    /** The outfit slot. Follows the type when there is one. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Category category;

    /** Up to three palette colors, main one first, stored as codes: "GREY,BROWN". */
    @Column(length = 60)
    private String colors;

    /** Older single free-text color; read only as a fallback for items saved before the palette existed. */
    @Column(name = "color_name", length = 30)
    private String colorName;

    @Column(length = 60)
    private String name;

    /** Comma-separated, e.g. "Casual,Work". */
    private String tags;

    @Column(length = 20)
    private String season;

    /** How large the item is drawn inside its frame, in percent (set in "Edit photo"). Null means 100. */
    private Integer scale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Item() {
    }

    public Item(Long userId, String imageFile, ItemType type, Category category, List<ItemColor> colors) {
        this.userId = userId;
        this.imageFile = imageFile;
        this.type = type;
        this.category = type != null ? type.getCategory() : category;
        setColors(colors);
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

    public ItemType getType() {
        return type;
    }

    /** Choosing a type also moves the item to that type's outfit slot. */
    public void setType(ItemType type) {
        this.type = type;
        if (type != null) {
            this.category = type.getCategory();
        }
    }

    public Category getCategory() {
        return category;
    }

    public int getScale() {
        return scale == null ? 100 : scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public void setImageFile(String imageFile) {
        this.imageFile = imageFile;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public List<ItemColor> getColors() {
        if (colors != null && !colors.isBlank()) {
            return ItemColor.fromCodes(Arrays.asList(colors.split(",")));
        }
        ItemColor legacy = ItemColor.from(colorName);
        return legacy == null ? List.of() : List.of(legacy);
    }

    public void setColors(List<ItemColor> newColors) {
        this.colors = newColors == null || newColors.isEmpty()
                ? null : newColors.stream().map(Enum::name).collect(Collectors.joining(","));
        this.colorName = null;
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
