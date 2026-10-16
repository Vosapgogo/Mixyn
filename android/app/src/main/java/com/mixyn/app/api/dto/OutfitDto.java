package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

import java.util.List;

/** A saved outfit: the picture of the whole look, its pieces, and what the user said about it. */
public class OutfitDto {

    /** One piece of an outfit: the item and the place it is worn in. */
    public static class Piece {
        public static final String TOP = "TOP";
        public static final String BOTTOM = "BOTTOM";
        public static final String SHOES = "SHOES";
        public static final String OUTERWEAR = "OUTERWEAR";
        /** A thin top worn under the top. */
        public static final String UNDER = "UNDER";
        public static final String BAG = "BAG";
        public static final String ACCESSORY = "ACCESSORY";

        public String slot;
        public ItemDto item;

        public Piece(String slot, ItemDto item) {
            this.slot = slot;
            this.item = item;
        }
    }

    public long id;
    /** Path on the backend, like an item photo. Use ApiClient.imageUrl(...) to load it. */
    public String imageUrl;
    /** In the order the card lists them: top, bottom, shoes, then the layers. */
    public List<Piece> pieces;
    /** The outfit's categories, from the same list as item tags ("Casual", "Work"…). */
    public List<String> tags;
    /** One or more of Spring / Summer / Autumn / Winter, or just "All". */
    public List<String> seasons;
    /** When it was saved, e.g. "2026-10-12T17:25:54Z". */
    public String createdAt;
    /** The last day it was worn according to the calendar ("2026-10-12"), or null. */
    @Nullable
    public String lastWornOn;
    /** How many days of the calendar have this outfit, the ones worn in an earlier version of it included. */
    public int wornDays;
    /**
     * Set only on an earlier version of an outfit, kept for the calendar days it was worn on before it
     * was changed: the id of the outfit as it is now. Such a version can't be changed.
     */
    @Nullable
    public Long replacedBy;
}
