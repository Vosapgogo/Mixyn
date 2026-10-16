package com.mixyn.outfit;

import com.mixyn.item.Category;
import com.mixyn.item.Item;
import com.mixyn.item.ItemType;

/**
 * The place a piece has in a saved outfit: one of the base slots (top, bottom, shoes) or one of the
 * layers around them. An outfit has at most one piece in every place, except accessories.
 */
public enum Slot {
    TOP("the top", "a top"),
    BOTTOM("the bottom", "a bottom"),
    SHOES("the shoes", "shoes"),
    OUTERWEAR("the outerwear", "outerwear"),
    UNDER("worn under the top", "something under the top"),
    BAG("the bag", "a bag"),
    ACCESSORY("an accessory", "an accessory");

    /** For messages: "in that outfit it is <role>", "that outfit already has <one>". */
    private final String role;
    private final String one;

    Slot(String role, String one) {
        this.role = role;
        this.one = one;
    }

    public String role() {
        return role;
    }

    public String one() {
        return one;
    }

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

    /** The same question for a type the item doesn't have yet: could a piece of this type be worn here? */
    public boolean accepts(ItemType type) {
        return this == UNDER ? type.canGoUnder()
                : type.getCategory() == category() || type.getAlsoCategory() == category();
    }

    /** The place a piece of this type takes in an outfit when it isn't worn as a layer of another kind. */
    public static Slot of(ItemType type) {
        return valueOf(type.getCategory().name());
    }

    private Category category() {
        return this == UNDER ? Category.TOP : Category.valueOf(name());
    }
}
