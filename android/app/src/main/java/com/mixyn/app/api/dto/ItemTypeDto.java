package com.mixyn.app.api.dto;

/** One entry of the backend's fixed list of clothing types. */
public class ItemTypeDto {
    public String code;      // "BLAZER"
    public String label;     // "Blazer"
    public String category;  // "OUTERWEAR"
    public String alsoCategory; // a second way to wear it ("OUTERWEAR" for a cardigan), usually null
}
