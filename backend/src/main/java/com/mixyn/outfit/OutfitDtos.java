package com.mixyn.outfit;

import com.mixyn.item.ItemDtos.ItemResponse;
import com.mixyn.outfit.StylistService.Look;

import jakarta.validation.constraints.Size;

public final class OutfitDtos {

    private OutfitDtos() {
    }

    public record GenerateRequest(@Size(max = 100) String hint) {
    }

    /** The stylist's look. Slots are full items, or null where the wardrobe has nothing to offer. */
    public record GenerateResponse(ItemResponse top, ItemResponse bottom, ItemResponse shoes, ItemResponse outerwear,
            boolean hintMatched, String message, String source) {

        static GenerateResponse from(Look look) {
            return new GenerateResponse(item(look.top()), item(look.bottom()), item(look.shoes()),
                    item(look.outerwear()), look.hintMatched(), look.message(), look.byAi() ? "AI" : "RANDOM");
        }

        private static ItemResponse item(com.mixyn.item.Item item) {
            return item == null ? null : ItemResponse.from(item);
        }
    }
}
