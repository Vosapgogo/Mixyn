package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

/** The stylist's look. A slot is null when the wardrobe has nothing for it (or no outerwear was added). */
public class GenerateResult {
    @Nullable
    public ItemDto top;
    @Nullable
    public ItemDto bottom;
    @Nullable
    public ItemDto shoes;
    @Nullable
    public ItemDto outerwear;
    public boolean hintMatched;
    /** A friendly note to show, e.g. when nothing matched the hint; null when there is nothing to say. */
    @Nullable
    public String message;
    /** "AI", or "RANDOM" when the stylist was unavailable. */
    public String source;
}
