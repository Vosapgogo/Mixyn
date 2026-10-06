package com.mixyn.app.api.dto;

import java.util.List;

public class ItemDto {
    public long id;
    /** Path on the backend, e.g. "/images/3f2a….png". Use ApiClient.imageUrl(...) to load it. */
    public String imageUrl;
    /** Code from the fixed type list ("BLAZER") and its label ("Blazer"); null when not chosen yet. */
    public String type;
    public String typeLabel;
    /** TOP, BOTTOM, SHOES, OUTERWEAR — or null when the AI couldn't tell. */
    public String category;
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
