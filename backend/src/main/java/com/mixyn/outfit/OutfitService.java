package com.mixyn.outfit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemRepository;
import com.mixyn.item.Labels;
import com.mixyn.outfit.Outfit.Piece;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.storage.ImageStorage;

/**
 * Saved outfits: what the user keeps from the canvas, with a picture of the whole look. And the outfit
 * calendar: which saved outfit was worn on which day.
 */
@Service
public class OutfitService {

    /** One piece of a saved outfit as a full item, with its place in the outfit. */
    public record WornPiece(Slot slot, Item item) {
    }

    /** A saved outfit together with its pieces, ready to be shown. lastWornOn is null if never worn. */
    public record OutfitView(Outfit outfit, List<WornPiece> pieces, LocalDate lastWornOn) {
    }

    /** One day of the calendar and the outfit worn on it. */
    public record DayView(LocalDate date, OutfitView outfit) {
    }

    /** The longest stretch of the calendar one request may ask for. */
    private static final int MAX_CALENDAR_DAYS = 366;

    private final OutfitRepository outfits;
    private final WearRepository wears;
    private final ItemRepository items;
    private final ImageStorage storage;

    public OutfitService(OutfitRepository outfits, WearRepository wears, ItemRepository items,
            ImageStorage storage) {
        this.outfits = outfits;
        this.wears = wears;
        this.items = items;
        this.storage = storage;
    }

    /**
     * Saves the outfit on the canvas. The app sends which item is worn where and a picture of the look;
     * the ids are checked against the user's own wardrobe before anything is stored.
     *
     * @param tags    the outfit's categories, or null for none
     * @param seasons the outfit's seasons, or null for none
     * @param wornOn  "Wear it today": the day to put the new outfit on in the calendar, or null to
     *                only save it. Both happen in one transaction: an outfit is never saved without
     *                its day, or the other way round.
     */
    @Transactional
    public OutfitView save(Long userId, LookIds look, byte[] picture, List<String> tags, List<String> seasons,
            LocalDate wornOn) {
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        List<Piece> pieces = checkLook(wardrobe, look);
        String tagList = tags == null ? null : Labels.joinTags(tags);
        String seasonList = seasons == null ? null : Labels.joinSeasons(seasons);
        if (wornOn != null) {
            checkDay(wornOn);
        }
        Outfit outfit = new Outfit(userId, storage.save(trim(picture)), pieces);
        outfit.setTags(tagList);
        outfit.setSeason(seasonList);
        outfit = outfits.save(outfit);
        if (wornOn != null) {
            putOnCalendar(userId, outfit.getId(), wornOn);
        }
        return view(outfit, wardrobe, wornOn);
    }

    /** The user's saved outfits, newest first. */
    public List<OutfitView> list(Long userId) {
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, LocalDate> lastWorn = lastWorn(wears.findByUserIdOrderByWornOn(userId));
        return outfits.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(outfit -> view(outfit, wardrobe, lastWorn.get(outfit.getId())))
                .toList();
    }

    // ----- The outfit calendar -----

    /**
     * "Wear it today" on a saved outfit: that day of the calendar now shows this outfit. A day holds one
     * outfit, so wearing another one on the same day replaces the first.
     */
    @Transactional
    public DayView wear(Long userId, Long outfitId, LocalDate day) {
        Outfit outfit = ownOutfit(userId, outfitId);
        checkDay(day);
        putOnCalendar(userId, outfitId, day);
        return new DayView(day, view(outfit, items.findByUserIdOrderByCreatedAtDesc(userId), lastWornOn(outfitId)));
    }

    /** The days from..to (both included) on which the user wore a saved outfit, oldest first. */
    public List<DayView> calendar(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_CALENDAR_DAYS).isBefore(to)) {
            throw invalid("Ask for up to a year of the calendar, from an earlier day to a later one");
        }
        List<Wear> all = wears.findByUserIdOrderByWornOn(userId);
        Map<Long, LocalDate> lastWorn = lastWorn(all);
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Outfit> saved = new HashMap<>();
        for (Outfit outfit : outfits.findByUserIdOrderByCreatedAtDesc(userId)) {
            saved.put(outfit.getId(), outfit);
        }
        List<DayView> days = new ArrayList<>();
        for (Wear wear : all) {
            Outfit outfit = saved.get(wear.getOutfitId());
            boolean inRange = !wear.getWornOn().isBefore(from) && !wear.getWornOn().isAfter(to);
            if (inRange && outfit != null) {
                days.add(new DayView(wear.getWornOn(), view(outfit, wardrobe, lastWorn.get(outfit.getId()))));
            }
        }
        return days;
    }

    private void putOnCalendar(Long userId, Long outfitId, LocalDate day) {
        Wear wear = wears.findByUserIdAndWornOn(userId, day).orElse(null);
        if (wear == null) {
            wears.save(new Wear(userId, outfitId, day));
        } else {
            wear.setOutfitId(outfitId);
            wears.save(wear);
        }
    }

    /**
     * The day comes from the phone, because "today" depends on where the user is. Tomorrow by the
     * server's clock can still be today for them; anything later can't have been worn yet.
     */
    private static void checkDay(LocalDate day) {
        if (day.isAfter(LocalDate.now().plusDays(1))) {
            throw invalid("A day that hasn't come yet can't be marked as worn");
        }
    }

    private LocalDate lastWornOn(Long outfitId) {
        return wears.findFirstByOutfitIdOrderByWornOnDesc(outfitId).map(Wear::getWornOn).orElse(null);
    }

    /** For every outfit that was ever worn, the last day it was. The wears must be in date order. */
    private static Map<Long, LocalDate> lastWorn(List<Wear> inDateOrder) {
        Map<Long, LocalDate> lastWorn = new HashMap<>();
        for (Wear wear : inDateOrder) {
            lastWorn.put(wear.getOutfitId(), wear.getWornOn());
        }
        return lastWorn;
    }

    // ----- Changing and deleting a saved outfit -----

    /** The user changes the categories or seasons on an outfit's card. Null fields stay as they are. */
    public OutfitView update(Long userId, Long outfitId, List<String> tags, List<String> seasons) {
        Outfit outfit = ownOutfit(userId, outfitId);
        if (tags != null) {
            outfit.setTags(Labels.joinTags(tags));
        }
        if (seasons != null) {
            outfit.setSeason(Labels.joinSeasons(seasons));
        }
        return view(outfits.save(outfit), items.findByUserIdOrderByCreatedAtDesc(userId), lastWornOn(outfitId));
    }

    /**
     * "Delete outfit": the outfit and its picture are gone for good, and so are its days in the
     * calendar. The clothes stay in the wardrobe.
     */
    @Transactional
    public void delete(Long userId, Long outfitId) {
        Outfit outfit = ownOutfit(userId, outfitId);
        wears.deleteByOutfitId(outfitId);
        outfits.delete(outfit);
        storage.delete(outfit.getImageFile());
    }

    /**
     * An item is leaving the wardrobe: it is taken out of every saved outfit that has it. The outfits
     * themselves stay, with their picture as it was saved.
     */
    public void forgetItem(Long itemId) {
        for (Outfit outfit : outfits.findDistinctByPiecesItemId(itemId)) {
            outfit.getPieces().removeIf(piece -> piece.getItemId().equals(itemId));
            outfits.save(outfit);
        }
    }

    /** Someone else's outfit is reported as "not found", so ids can't be probed. */
    private Outfit ownOutfit(Long userId, Long outfitId) {
        return outfits.findById(outfitId)
                .filter(outfit -> outfit.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Outfit not found"));
    }

    /**
     * The trust boundary of saving: the app names the pieces by id, and none of it is taken on trust.
     * Every id must be an item of this wardrobe (someone else's item and a made-up id look the same
     * here: not found), every item must be wearable in the place it is given, and no item is worn twice.
     * The pieces come back in the order the outfit's card lists them.
     */
    static List<Piece> checkLook(List<Item> wardrobe, LookIds look) {
        Map<Long, Item> owned = byId(wardrobe);
        List<Piece> pieces = new ArrayList<>();
        add(pieces, Slot.TOP, look.topId(), owned);
        add(pieces, Slot.BOTTOM, look.bottomId(), owned);
        add(pieces, Slot.SHOES, look.shoesId(), owned);
        add(pieces, Slot.OUTERWEAR, look.outerwearId(), owned);
        add(pieces, Slot.UNDER, look.underId(), owned);
        add(pieces, Slot.BAG, look.bagId(), owned);
        if (look.accessoryIds() != null) {
            for (Long id : look.accessoryIds()) {
                add(pieces, Slot.ACCESSORY, id, owned);
            }
        }
        if (pieces.isEmpty()) {
            throw invalid("An outfit needs at least one piece");
        }
        return pieces;
    }

    private static void add(List<Piece> pieces, Slot slot, Long itemId, Map<Long, Item> owned) {
        if (itemId == null) {
            return; // nothing is worn in this place
        }
        Item item = owned.get(itemId);
        if (item == null) {
            throw invalid("This outfit has a piece that isn't in your wardrobe");
        }
        if (!slot.accepts(item)) {
            throw invalid("One of the pieces can't be worn where the outfit has it");
        }
        if (pieces.stream().anyMatch(piece -> piece.getItemId().equals(itemId))) {
            throw invalid("A piece can be worn only once in an outfit");
        }
        pieces.add(new Piece(slot, itemId));
    }

    /** The picture is cropped to the outfit itself and scaled down, like an item photo. */
    private byte[] trim(byte[] picture) {
        try {
            return storage.cropToItem(picture);
        } catch (ApiException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_image", "The outfit's picture can't be read");
        }
    }

    private static OutfitView view(Outfit outfit, List<Item> wardrobe, LocalDate lastWornOn) {
        Map<Long, Item> owned = byId(wardrobe);
        List<WornPiece> pieces = new ArrayList<>();
        for (Piece piece : outfit.getPieces()) {
            Item item = owned.get(piece.getItemId());
            if (item != null) {
                pieces.add(new WornPiece(piece.getSlot(), item));
            }
        }
        return new OutfitView(outfit, pieces, lastWornOn);
    }

    private static Map<Long, Item> byId(List<Item> wardrobe) {
        Map<Long, Item> owned = new HashMap<>();
        for (Item item : wardrobe) {
            owned.put(item.getId(), item);
        }
        return owned;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }
}
