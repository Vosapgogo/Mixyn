package com.mixyn.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The fixed color palette. Like ItemType, both the AI and the user choose from it, so the same color
 * always has the same name (which is what lets the stylist match a hint such as "burgundy").
 * An item can have up to MAX_PER_ITEM colors, the main one first.
 */
public enum ItemColor {
    BLACK("Black", "#1F1B17"),
    WHITE("White", "#FFFFFF"),
    CREAM("Cream", "#F2ECE3"),
    GREY("Grey", "#9A9A9A"),
    BEIGE("Beige", "#D9C7A8"),
    BROWN("Brown", "#6B4A34"),
    KHAKI("Khaki", "#8A8468"),
    OLIVE("Olive", "#6F7254"),
    GREEN("Green", "#4E7A4B"),
    NAVY("Navy", "#1F2A44"),
    BLUE("Blue", "#4C74A6"),
    LIGHT_BLUE("Light Blue", "#A9C4E0"),
    PURPLE("Purple", "#6E4A8E"),
    PINK("Pink", "#E3A6B5"),
    RED("Red", "#B3261E"),
    BURGUNDY("Burgundy", "#6D1F2B"),
    ORANGE("Orange", "#D9822B"),
    YELLOW("Yellow", "#E6C84F");

    public static final int MAX_PER_ITEM = 3;

    private final String label;
    private final String hex;

    ItemColor(String label, String hex) {
        this.label = label;
        this.hex = hex;
    }

    public String getLabel() {
        return label;
    }

    public String getHex() {
        return hex;
    }

    /** The color for a code like "LIGHT_BLUE" or a label like "Light Blue"; null if it isn't in the palette. */
    public static ItemColor from(String codeOrLabel) {
        if (codeOrLabel == null) {
            return null;
        }
        String wanted = codeOrLabel.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (ItemColor color : values()) {
            if (color.name().equals(wanted)) {
                return color;
            }
        }
        return null;
    }

    /** Valid palette colors from a list of codes: unknown ones and repeats are dropped, at most MAX_PER_ITEM kept. */
    public static List<ItemColor> fromCodes(Iterable<String> codes) {
        List<ItemColor> colors = new ArrayList<>();
        for (String code : codes) {
            ItemColor color = from(code);
            if (color != null && !colors.contains(color) && colors.size() < MAX_PER_ITEM) {
                colors.add(color);
            }
        }
        return colors;
    }
}
