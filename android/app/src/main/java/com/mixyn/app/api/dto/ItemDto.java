package com.mixyn.app.api.dto;

import java.util.List;

public class ItemDto {
    public long id;
    /** Path on the backend, e.g. "/images/3f2a….png". Use ApiClient.imageUrl(...) to load it. */
    public String imageUrl;
    /** TOP, BOTTOM, SHOES, OUTERWEAR — or null when the AI couldn't tell. */
    public String category;
    public String colorName;
    public String colorHex;
    public String name;
    public List<String> tags;
    public String season;
}
