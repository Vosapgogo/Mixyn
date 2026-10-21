package com.mixyn.app.api.dto;

import java.util.List;

/**
 * Where an item is worn, asked before "Remove from wardrobe": how many saved outfits have it, and the
 * calendar days ("2026-10-12") whose outfit had it, earliest first.
 */
public class ItemUseDto {
    public int outfits;
    public List<String> wornOn;
}
