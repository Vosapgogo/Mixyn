package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * The stylist's look. A slot is null when the wardrobe has nothing for it; the layers (outerwear, the
 * piece under the top, bag, accessories) are null / empty when the stylist left them off.
 */
public class GenerateResult {
    @Nullable
    public ItemDto top;
    @Nullable
    public ItemDto bottom;
    @Nullable
    public ItemDto shoes;
    @Nullable
    public ItemDto outerwear;
    /** The piece worn under the top. */
    @Nullable
    public ItemDto under;
    @Nullable
    public ItemDto bag;
    @Nullable
    public List<ItemDto> accessories;
    public boolean hintMatched;
    /** A friendly note to show, e.g. when nothing matched the hint; null when there is nothing to say. */
    @Nullable
    public String message;
    /** "AI", or "RANDOM" when the stylist was unavailable. */
    public String source;
}
