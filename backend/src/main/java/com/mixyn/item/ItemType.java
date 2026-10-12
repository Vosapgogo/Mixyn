package com.mixyn.item;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The fixed list of clothing types. Both the AI and the user must pick from it, so there are never
 * free-text categories. Every type belongs to one outfit slot or layer (Category). A few can be worn
 * two ways and have a second one: a cardigan is a top, or an outer layer over another top.
 * To add a type, add a line here; nothing else needs to change.
 */
public enum ItemType {
    // Tops
    T_SHIRT("T-shirt", Category.TOP),
    SHIRT("Shirt", Category.TOP),
    BLOUSE("Blouse", Category.TOP),
    TOP("Top", Category.TOP),
    SWEATER("Sweater", Category.TOP),
    HOODIE("Hoodie", Category.TOP),
    SWEATSHIRT("Sweatshirt", Category.TOP),
    CARDIGAN("Cardigan", Category.TOP, Category.OUTERWEAR),
    DRESS("Dress", Category.TOP),
    // Bottoms
    JEANS("Jeans", Category.BOTTOM),
    TROUSERS("Trousers", Category.BOTTOM),
    SHORTS("Shorts", Category.BOTTOM),
    SKIRT("Skirt", Category.BOTTOM),
    LEGGINGS("Leggings", Category.BOTTOM),
    // Outerwear
    BLAZER("Blazer", Category.OUTERWEAR),
    JACKET("Jacket", Category.OUTERWEAR),
    COAT("Coat", Category.OUTERWEAR),
    TRENCH_COAT("Trench coat", Category.OUTERWEAR),
    PUFFER_JACKET("Puffer jacket", Category.OUTERWEAR),
    PUFFER_COAT("Puffer coat", Category.OUTERWEAR),
    DENIM_JACKET("Denim jacket", Category.OUTERWEAR),
    LEATHER_JACKET("Leather jacket", Category.OUTERWEAR),
    VEST("Vest", Category.OUTERWEAR),
    ZIP_UP("Zip-up", Category.OUTERWEAR),
    // Shoes
    SNEAKERS("Sneakers", Category.SHOES),
    BOOTS("Boots", Category.SHOES),
    ANKLE_BOOTS("Ankle boots", Category.SHOES),
    HEELS("Heels", Category.SHOES),
    FLATS("Flats", Category.SHOES),
    LOAFERS("Loafers", Category.SHOES),
    SANDALS("Sandals", Category.SHOES),
    // Bags
    HANDBAG("Handbag", Category.BAG),
    TOTE_BAG("Tote bag", Category.BAG),
    CROSSBODY_BAG("Crossbody bag", Category.BAG),
    BACKPACK("Backpack", Category.BAG),
    CLUTCH("Clutch", Category.BAG),
    // Accessories
    BEANIE("Beanie", Category.ACCESSORY),
    HAT("Hat", Category.ACCESSORY),
    CAP("Cap", Category.ACCESSORY),
    HEADSCARF("Headscarf", Category.ACCESSORY),
    SCARF("Scarf", Category.ACCESSORY),
    GLOVES("Gloves", Category.ACCESSORY),
    SUNGLASSES("Sunglasses", Category.ACCESSORY),
    BELT("Belt", Category.ACCESSORY),
    JEWELRY("Jewelry", Category.ACCESSORY);

    private final String label;
    private final Category category;
    /** The other way to wear it, or null for the usual one-way types. */
    private final Category alsoCategory;

    ItemType(String label, Category category) {
        this(label, category, null);
    }

    ItemType(String label, Category category, Category alsoCategory) {
        this.label = label;
        this.category = category;
        this.alsoCategory = alsoCategory;
    }

    public String getLabel() {
        return label;
    }

    public Category getCategory() {
        return category;
    }

    public Category getAlsoCategory() {
        return alsoCategory;
    }

    /** Types whose name contains the typed text ("bl" → Blazer, Blouse); names starting with it come first. */
    public static List<ItemType> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(t -> t.label.toLowerCase(Locale.ROOT).contains(q))
                .sorted((a, b) -> Boolean.compare(
                        !a.label.toLowerCase(Locale.ROOT).startsWith(q),
                        !b.label.toLowerCase(Locale.ROOT).startsWith(q)))
                .toList();
    }

    /** The type for a code like "BLAZER", or null if there is no such type. */
    public static ItemType fromCode(String code) {
        try {
            return code == null ? null : valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
