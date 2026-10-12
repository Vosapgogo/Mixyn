package com.mixyn.outfit;

import com.mixyn.item.Category;
import com.mixyn.item.Item;

/**
 * The place a piece has in a saved outfit: one of the base slots (top, bottom, shoes) or one of the
 * layers around them. An outfit has at most one piece in every place, except accessories.
 */
public enum Slot {
    TOP, BOTTOM, SHOES, OUTERWEAR, UNDER, BAG, ACCESSORY;

    /** Whether this item can be worn in this place (a cardigan as outerwear, a T-shirt under a sweater). */
    public boolean accepts(Item item) {
        return switch (this) {
            case TOP -> item.fits(Category.TOP);
            case BOTTOM -> item.fits(Category.BOTTOM);
            case SHOES -> item.fits(Category.SHOES);
            case OUTERWEAR -> item.fits(Category.OUTERWEAR);
            case UNDER -> item.canGoUnder();
            case BAG -> item.fits(Category.BAG);
            case ACCESSORY -> item.fits(Category.ACCESSORY);
        };
    }
}
