package com.mixyn.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class ItemDtos {

    private ItemDtos() {
    }

    /** Body of PATCH /api/items/{id}: only the fields that are present are changed. */
    public record ItemUpdate(String type, String colorName, String colorHex, String name, List<String> tags,
            String season) {
    }

    /**
     * Body of POST /api/items/{id}/photo ("Edit photo" → Done): turn the picture clockwise by
     * 0/90/180/270 degrees, optionally mirror it left-right, and set the display size in percent.
     */
    public record PhotoEdit(int rotation, boolean mirror, int scale) {
    }

    public record ItemResponse(Long id, String imageUrl, String type, String typeLabel, Category category,
            String colorName, String colorHex, int scale,
            String name, List<String> tags, String season, Instant createdAt) {

        public static ItemResponse from(Item item) {
            List<String> tags = item.getTags() == null || item.getTags().isBlank()
                    ? List.of()
                    : Arrays.asList(item.getTags().split(","));
            ItemType type = item.getType();
            return new ItemResponse(item.getId(), "/images/" + item.getImageFile(),
                    type == null ? null : type.name(), type == null ? null : type.getLabel(), item.getCategory(),
                    item.getColorName(), item.getColorHex(), item.getScale(), item.getName(), tags, item.getSeason(),
                    item.getCreatedAt());
        }
    }
}
