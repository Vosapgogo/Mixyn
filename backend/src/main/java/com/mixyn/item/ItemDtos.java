package com.mixyn.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class ItemDtos {

    private ItemDtos() {
    }

    /** "Work,Cozy" → ["Work", "Cozy"]; null or empty → []. */
    private static List<String> split(String commaSeparated) {
        return commaSeparated == null || commaSeparated.isBlank()
                ? List.of() : Arrays.asList(commaSeparated.split(","));
    }

    /**
     * Body of PATCH /api/items/{id}: only the fields that are present are changed.
     * openFront: the user says the piece is worn open down the front (true) or closed (false).
     */
    public record ItemUpdate(String type, List<String> colors, String name, List<String> tags,
            List<String> seasons, Boolean openFront) {
    }

    public record ColorResponse(String code, String label, String hex) {
    }

    /**
     * Body of POST /api/items/{id}/photo ("Edit photo" → Done): turn the picture clockwise by
     * 0/90/180/270 degrees, optionally mirror it left-right, and set the display size in percent.
     */
    public record PhotoEdit(int rotation, boolean mirror, int scale) {
    }

    /**
     * alsoCategory: a second slot the item can be worn in (a cardigan as OUTERWEAR), usually null.
     * underLayer: true for a thin top that can also be worn under another top.
     * opening: for a piece photographed open down the front, [neckLeft, neckRight, hemLeft, hemRight] in
     * thousandths of the picture's width; empty for closed pieces.
     */
    public record ItemResponse(Long id, String imageUrl, String type, String typeLabel, Category category,
            Category alsoCategory, boolean underLayer, List<Integer> opening, List<ColorResponse> colors, String colorName, String colorHex, int scale,
            String name, List<String> tags, List<String> seasons, Instant createdAt) {

        public static ItemResponse from(Item item) {
            List<String> tags = split(item.getTags());
            ItemType type = item.getType();
            List<ColorResponse> colors = item.getColors().stream()
                    .map(c -> new ColorResponse(c.name(), c.getLabel(), c.getHex())).toList();
            // colorName / colorHex repeat the main (first) color for places that show just one.
            ColorResponse main = colors.isEmpty() ? null : colors.get(0);
            return new ItemResponse(item.getId(), "/images/" + item.getImageFile(),
                    type == null ? null : type.name(), type == null ? null : type.getLabel(), item.getCategory(),
                    item.getAlsoCategory(), item.canGoUnder(), item.getOpening(), colors, main == null ? null : main.label(), main == null ? null : main.hex(), item.getScale(), item.getName(), tags, split(item.getSeason()),
                    item.getCreatedAt());
        }
    }
}
