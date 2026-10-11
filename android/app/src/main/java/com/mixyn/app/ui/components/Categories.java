package com.mixyn.app.ui.components;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.mixyn.app.R;

/** Turns the backend's category codes into the labels shown to the user. */
public final class Categories {

    public static final String TOP = "TOP";
    public static final String BOTTOM = "BOTTOM";
    public static final String SHOES = "SHOES";
    public static final String OUTERWEAR = "OUTERWEAR";
    public static final String BAG = "BAG";
    public static final String ACCESSORY = "ACCESSORY";

    private Categories() {
    }

    @StringRes
    public static int label(@Nullable String category) {
        if (category == null) return R.string.category_unknown;
        return switch (category) {
            case TOP -> R.string.category_top;
            case BOTTOM -> R.string.category_bottom;
            case SHOES -> R.string.category_shoes;
            case OUTERWEAR -> R.string.category_outerwear;
            case BAG -> R.string.category_bag;
            case ACCESSORY -> R.string.category_accessory;
            default -> R.string.category_unknown;
        };
    }
}
