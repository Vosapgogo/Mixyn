package com.mixyn.app.ui.canvas;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;

import com.mixyn.app.R;

import java.util.List;

/**
 * Where an accessory is drawn on the outfit. Accessories that would sit in the same place share a spot
 * (a beanie and a cap are both on the head), so one outfit shows at most one of them and the user swipes
 * between them. The rectangle is given in shares of the canvas: x of its width, y of the outfit's height
 * (0 = top of the top, 1 = bottom of the shoes; the head sits above the top, so it starts below zero).
 */
enum AccessorySpot {
    WAIST(R.id.canvas_waist, 0f, 1f, 0.33f, 0.43f, "BELT"),
    NECK(R.id.canvas_neck, 0f, 1f, 0.02f, 0.24f, "SCARF"),
    // The head ends where the top begins, so a hat never hides the collar; the canvas lowers it to the
    // shoulder line of the top that is on.
    HEAD(R.id.canvas_head, 0f, 1f, -1f, 0f, "BEANIE", "HAT", "CAP", "HEADSCARF"),
    SIDE(R.id.canvas_side, 0.70f, 0.97f, 0.04f, 0.18f, "SUNGLASSES", "JEWELRY"),
    HANDS(R.id.canvas_hands, 0.70f, 0.97f, 0.36f, 0.50f, "GLOVES");

    @IdRes
    final int pagerId;
    final float left, right, top, bottom;
    /** Item type codes drawn here. */
    final List<String> types;

    AccessorySpot(@IdRes int pagerId, float left, float right, float top, float bottom, String... types) {
        this.pagerId = pagerId;
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.types = List.of(types);
    }

    /** The spot for an item type like "SCARF", or null for a type that is not a known accessory. */
    @Nullable
    static AccessorySpot of(@Nullable String type) {
        for (AccessorySpot spot : values()) {
            if (spot.types.contains(type)) return spot;
        }
        return null;
    }
}
