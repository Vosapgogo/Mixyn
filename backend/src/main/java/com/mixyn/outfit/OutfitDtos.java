package com.mixyn.outfit;

import com.mixyn.item.ItemDtos.ItemResponse;
import com.mixyn.outfit.StylistService.Look;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public final class OutfitDtos {

    private OutfitDtos() {
    }

    /** Item ids of an outfit as the app shows it; any slot may be null (accessoryIds: null or empty). */
    public record LookIds(Long topId, Long bottomId, Long shoesId, Long outerwearId, Long underId, Long bagId,
            @Size(max = 8) List<Long> accessoryIds) {
    }

    /** One earlier exchange in this styling session: what the user asked and which outfit they got. */
    public record PastTurn(@Size(max = 500) String hint, @Valid LookIds look) {
    }

    /**
     * @param hint    what the user wants now: a word or a full description (optional)
     * @param history earlier requests of this session, oldest first, so follow-ups like
     *                "warmer" or "change only the shoes" make sense (optional)
     * @param current the outfit on screen right now, including manual swipes (optional)
     */
    public record GenerateRequest(@Size(max = 500) String hint, @Valid @Size(max = 6) List<PastTurn> history,
            @Valid LookIds current) {
    }

    /**
     * The stylist's look. Slots are full items, or null where the wardrobe has nothing to offer; the
     * layers (outerwear, the piece under the top, bag, accessories) are null / empty when the stylist
     * left them off.
     */
    public record GenerateResponse(ItemResponse top, ItemResponse bottom, ItemResponse shoes, ItemResponse outerwear,
            ItemResponse under, ItemResponse bag, List<ItemResponse> accessories, boolean hintMatched, String message, String source) {

        static GenerateResponse from(Look look) {
            return new GenerateResponse(item(look.top()), item(look.bottom()), item(look.shoes()),
                    item(look.outerwear()), item(look.under()), item(look.bag()),
                    look.accessories().stream().map(ItemResponse::from).toList(),
                    look.hintMatched(), look.message(), look.byAi() ? "AI" : "RANDOM");
        }

        private static ItemResponse item(com.mixyn.item.Item item) {
            return item == null ? null : ItemResponse.from(item);
        }
    }
}
