package com.mixyn.outfit;

import com.mixyn.item.ItemDtos.ItemResponse;
import com.mixyn.item.Labels;
import com.mixyn.outfit.OutfitService.DayView;
import com.mixyn.outfit.OutfitService.OutfitView;
import com.mixyn.outfit.StylistService.Look;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    /**
     * The "outfit" part of POST /api/users/{id}/outfits (the other part is the picture).
     *
     * @param look    which item is worn where
     * @param tags    the outfit's categories, from the same list as item tags (optional)
     * @param seasons the outfit's seasons (optional)
     * @param wornOn  "Wear it today": the user's today, e.g. "2026-10-12"; the outfit is saved and put
     *                on that day of the calendar (optional)
     */
    public record SaveRequest(@Valid @NotNull LookIds look, @Size(max = 8) List<String> tags,
            @Size(max = 5) List<String> seasons, LocalDate wornOn) {
    }

    /** Body of PUT /api/users/{id}/calendar/{date}: the saved outfit worn on that day. */
    public record WearRequest(@NotNull Long outfitId) {
    }

    /** Body of PATCH /api/outfits/{id}: only the fields that are present are changed. */
    public record OutfitUpdate(@Size(max = 8) List<String> tags, @Size(max = 5) List<String> seasons) {
    }

    /** One piece of a saved outfit: the item and where it is worn (TOP, UNDER, ACCESSORY…). */
    public record PieceResponse(Slot slot, ItemResponse item) {
    }

    /**
     * A saved outfit. imageUrl is the picture of the whole look, served like an item photo;
     * lastWornOn is the last day it was worn according to the calendar, or null; wornDays counts its
     * days in the calendar, those worn in an earlier version of it included; replacedBy is set on an
     * earlier version kept for the calendar (the id of the outfit as it is now), else null.
     */
    public record OutfitResponse(Long id, String imageUrl, List<PieceResponse> pieces, List<String> tags,
            List<String> seasons, Instant createdAt, LocalDate lastWornOn, int wornDays, Long replacedBy) {

        static OutfitResponse from(OutfitView view) {
            Outfit outfit = view.outfit();
            return new OutfitResponse(outfit.getId(), "/images/" + outfit.getImageFile(),
                    view.pieces().stream().map(p -> new PieceResponse(p.slot(), ItemResponse.from(p.item()))).toList(),
                    Labels.split(outfit.getTags()), Labels.split(outfit.getSeason()), outfit.getCreatedAt(),
                    view.lastWornOn(), view.wornDays(), outfit.getReplacedBy());
        }
    }

    /** One day of the calendar: the date ("2026-10-12") and the outfit worn on it. */
    public record DayResponse(LocalDate date, OutfitResponse outfit) {

        static DayResponse from(DayView day) {
            return new DayResponse(day.date(), OutfitResponse.from(day.outfit()));
        }
    }
}
