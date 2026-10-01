package com.mixyn.item;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class ItemDtos {

    private ItemDtos() {
    }

    public record ItemResponse(Long id, String imageUrl, Category category, String colorName, String colorHex,
            String name, List<String> tags, String season, Instant createdAt) {

        public static ItemResponse from(Item item) {
            List<String> tags = item.getTags() == null || item.getTags().isBlank()
                    ? List.of()
                    : Arrays.asList(item.getTags().split(","));
            return new ItemResponse(item.getId(), "/images/" + item.getImageFile(), item.getCategory(),
                    item.getColorName(), item.getColorHex(), item.getName(), tags, item.getSeason(),
                    item.getCreatedAt());
        }
    }
}
