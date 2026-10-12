package com.mixyn.app.api.dto;

import java.util.List;

public class ItemDto {
    public long id;
    /** Path on the backend, e.g. "/images/3f2a….png". Use ApiClient.imageUrl(...) to load it. */
    public String imageUrl;
    /** Code from the fixed type list ("BLAZER") and its label ("Blazer"); null when not chosen yet. */
    public String type;
    public String typeLabel;
    /** TOP, BOTTOM, SHOES, OUTERWEAR, BAG, ACCESSORY — or null when the AI couldn't tell. */
    public String category;
    /** A second slot the item can be worn in (a cardigan: "OUTERWEAR"); null for most items. */
    public String alsoCategory;
    /** True for a thin top (T-shirt, shirt…) that can also be worn under another top. */
    public boolean underLayer;
    /** Up to three palette colors, the main one first. colorName / colorHex repeat the first. */
    public List<ColorDto> colors;
    public String colorName;
    public String colorHex;
    /** How large the item is drawn inside its frame, in percent (100 = as is). */
    public int scale = 100;
    public String name;
    public List<String> tags;
    /** One or more of Spring / Summer / Autumn / Winter, or just "All". */
    public List<String> seasons;
}
