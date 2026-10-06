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

    public record RotateRequest(int degrees) {
    }

    public record ItemResponse(Long id, String imageUrl, String type, String typeLabel, Category category,
            String colorName, String colorHex,
            String name, List<String> tags, String season, Instant createdAt) {

        public static ItemResponse from(Item item) {
            List<String> tags = item.getTags() == null || item.getTags().isBlank()
                    ? List.of()
                    : Arrays.asList(item.getTags().split(","));
            ItemType type = item.getType();
            return new ItemResponse(item.getId(), "/images/" + item.getImageFile(),
                    type == null ? null : type.name(), type == null ? null : type.getLabel(), item.getCategory(),
                    item.getColorName(), item.getColorHex(), item.getName(), tags, item.getSeason(),
                    item.getCreatedAt());
        }
    }
}
