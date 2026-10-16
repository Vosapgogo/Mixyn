package com.mixyn.outfit;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemRepository;
import com.mixyn.item.ItemType;
import com.mixyn.item.Labels;
import com.mixyn.outfit.Outfit.Piece;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.storage.ImageStorage;

/**
 * Saved outfits: what the user keeps from the canvas, with a picture of the whole look. And the outfit
 * calendar: which saved outfit was worn on which day.
 *
 * The calendar is a record of what was worn, so changing an outfit never changes a day that is already
 * in it: before a worn outfit gets its new look, its days move to a copy of the outfit as it was (see
 * Outfit.getReplacedBy). Copies are left out of the Outfits list and can't be changed themselves.
 */
@Service
public class OutfitService {

    /** One piece of a saved outfit as a full item, with its place in the outfit. */
    public record WornPiece(Slot slot, Item item) {
    }

    /**
     * A saved outfit together with its pieces, ready to be shown. lastWornOn is null if never worn (in
     * this version); wornDays counts its days in the calendar, the ones of its earlier versions included.
     */
    public record OutfitView(Outfit outfit, List<WornPiece> pieces, LocalDate lastWornOn, int wornDays) {
    }

    /** One day of the calendar and the outfit worn on it. */
    public record DayView(LocalDate date, OutfitView outfit) {
    }

    /** A piece of an outfit that has to move to another place because its type changed. */
    record Move(Outfit outfit, int position, Slot to) {
    }

    /** A saved outfit in which a piece can't take its new place, because another piece is there. */
    record Conflict(Outfit outfit, Slot from, Slot to, Item taking) {
    }

    /** What changing a piece's type does to the saved outfits that have it. */
    record TypeChange(List<Move> moves, List<Conflict> conflicts) {
    }

    /** The longest stretch of the calendar one request may ask for. */
    private static final int MAX_CALENDAR_DAYS = 366;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

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
        refuseDuplicate(userId, pieces, null);
        Outfit outfit = new Outfit(userId, storage.save(trim(picture)), pieces);
        outfit.setTags(tagList);
        outfit.setSeason(seasonList);
        outfit = outfits.save(outfit);
        if (wornOn != null) {
            putOnCalendar(userId, outfit.getId(), wornOn);
        }
        return view(outfit, wardrobe, wornOn, wornOn == null ? 0 : 1);
    }

    /** The user's saved outfits, newest first. */
    public List<OutfitView> list(Long userId) {
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        List<Wear> all = wears.findByUserIdOrderByWornOn(userId);
        Map<Long, LocalDate> lastWorn = lastWorn(all);
        Map<Long, Integer> days = wornDays(all, outfits.findByUserIdOrderByCreatedAtDesc(userId));
        return outfits.findByUserIdAndReplacedByIsNullOrderByCreatedAtDesc(userId).stream()
                .map(outfit -> view(outfit, wardrobe, lastWorn.get(outfit.getId()), days.getOrDefault(outfit.getId(), 0)))
                .toList();
    }

    /**
     * How many calendar days every outfit has, counted for the outfit as it is now: a day worn in an
     * earlier version counts for the outfit it became.
     */
    private static Map<Long, Integer> wornDays(List<Wear> all, List<Outfit> every) {
        Map<Long, Long> nowOf = new HashMap<>();
        for (Outfit outfit : every) {
            if (outfit.getReplacedBy() != null) {
                nowOf.put(outfit.getId(), outfit.getReplacedBy());
            }
        }
        Map<Long, Integer> days = new HashMap<>();
        for (Wear wear : all) {
            days.merge(nowOf.getOrDefault(wear.getOutfitId(), wear.getOutfitId()), 1, Integer::sum);
        }
        return days;
    }

    /** The same for one outfit: its own days and those of its earlier versions. */
    private int wornDays(Long outfitId) {
        long days = wears.countByOutfitId(outfitId);
        for (Outfit version : outfits.findByReplacedBy(outfitId)) {
            days += wears.countByOutfitId(version.getId());
        }
        return (int) days;
    }

    // ----- The outfit calendar -----

    /**
     * "Wear it today" on a saved outfit: that day of the calendar now shows this outfit. A day holds one
     * outfit, so wearing another one on the same day replaces the first.
     */
    @Transactional
    public DayView wear(Long userId, Long outfitId, LocalDate day) {
        Outfit outfit = ownCurrentOutfit(userId, outfitId);
        checkDay(day);
        putOnCalendar(userId, outfitId, day);
        return new DayView(day, view(outfit, items.findByUserIdOrderByCreatedAtDesc(userId), lastWornOn(outfitId),
                wornDays(outfitId)));
    }

    /** The days from..to (both included) on which the user wore a saved outfit, oldest first. */
    public List<DayView> calendar(Long userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_CALENDAR_DAYS).isBefore(to)) {
            throw invalid("Ask for up to a year of the calendar, from an earlier day to a later one");
        }
        List<Wear> all = wears.findByUserIdOrderByWornOn(userId);
        Map<Long, LocalDate> lastWorn = lastWorn(all);
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        List<Outfit> every = outfits.findByUserIdOrderByCreatedAtDesc(userId);
        Map<Long, Integer> wornDays = wornDays(all, every);
        Map<Long, Outfit> saved = new HashMap<>();
        for (Outfit outfit : every) {
            saved.put(outfit.getId(), outfit);
        }
        List<DayView> days = new ArrayList<>();
        for (Wear wear : all) {
            Outfit outfit = saved.get(wear.getOutfitId());
            boolean inRange = !wear.getWornOn().isBefore(from) && !wear.getWornOn().isAfter(to);
            if (inRange && outfit != null) {
                days.add(new DayView(wear.getWornOn(), view(outfit, wardrobe, lastWorn.get(outfit.getId()),
                        wornDays.getOrDefault(outfit.getId(), 0))));
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
        Outfit outfit = ownCurrentOutfit(userId, outfitId);
        if (tags != null) {
            outfit.setTags(Labels.joinTags(tags));
        }
        if (seasons != null) {
            outfit.setSeason(Labels.joinSeasons(seasons));
        }
        return view(outfits.save(outfit), items.findByUserIdOrderByCreatedAtDesc(userId), lastWornOn(outfitId),
                wornDays(outfitId));
    }

    /**
     * "Update" after editing an outfit on the canvas: the outfit keeps its id and its date, and gets the
     * new pieces, picture, categories and seasons. The new look is checked like a new one, and it may not
     * become a copy of another saved outfit. If the outfit was worn, its days first move to a copy of it
     * as it was, with the old picture: those days keep showing what was worn on them.
     */
    @Transactional
    public OutfitView replace(Long userId, Long outfitId, LookIds look, byte[] picture, List<String> tags,
            List<String> seasons) {
        Outfit outfit = ownCurrentOutfit(userId, outfitId);
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        List<Piece> pieces = checkLook(wardrobe, look);
        String tagList = tags == null ? null : Labels.joinTags(tags);
        String seasonList = seasons == null ? null : Labels.joinSeasons(seasons);
        refuseDuplicate(userId, pieces, outfitId);
        String oldPicture = outfit.getImageFile();
        List<Wear> worn = wears.findByOutfitId(outfitId);
        if (!worn.isEmpty()) {
            Outfit asWorn = outfits.save(outfit.versionAsWorn()); // it keeps the old picture file
            for (Wear wear : worn) {
                wear.setOutfitId(asWorn.getId());
            }
            wears.saveAll(worn);
        }
        outfit.getPieces().clear();
        outfit.getPieces().addAll(pieces);
        outfit.setImageFile(storage.save(trim(picture)));
        outfit.setTags(tagList);
        outfit.setSeason(seasonList);
        // Its worn days are the old version's now, but they still count for it.
        OutfitView view = view(outfits.save(outfit), wardrobe, null, wornDays(outfitId));
        if (worn.isEmpty()) {
            storage.delete(oldPicture); // a new file name, so the app doesn't keep showing a cached picture
        }
        return view;
    }

    /** Saving the same pieces in the same places twice makes no sense; the user is told where the first one is. */
    private void refuseDuplicate(Long userId, List<Piece> pieces, Long exceptOutfitId) {
        Outfit same = duplicateOf(outfits.findByUserIdAndReplacedByIsNullOrderByCreatedAtDesc(userId), pieces,
                exceptOutfitId);
        if (same != null) {
            throw new ApiException(HttpStatus.CONFLICT, "duplicate_outfit",
                    "You already have this outfit, saved on " + savedOn(same) + ".",
                    Map.of("outfitId", same.getId()));
        }
    }

    /** The outfit (other than exceptOutfitId) with exactly these pieces in exactly these places, or null. */
    static Outfit duplicateOf(List<Outfit> saved, List<Piece> pieces, Long exceptOutfitId) {
        Set<String> wanted = placesOf(pieces);
        for (Outfit outfit : saved) {
            if (!outfit.getId().equals(exceptOutfitId) && placesOf(outfit.getPieces()).equals(wanted)) {
                return outfit;
            }
        }
        return null;
    }

    private static Set<String> placesOf(List<Piece> pieces) {
        Set<String> places = new HashSet<>();
        for (Piece piece : pieces) {
            places.add(piece.getSlot() + ":" + piece.getItemId());
        }
        return places;
    }

    // ----- A piece's type changes -----

    /**
     * Called before a piece gets a new type on its card. In a saved outfit where the piece can't be worn
     * in its place any more (a T-shirt that is really a skirt is no top), it moves to the place its new
     * type takes. If that place is already taken in some outfit (the outfit has a bottom), nothing
     * changes anywhere and the user is told which outfit to change first.
     */
    @Transactional
    public void fitTypeChange(Item item, ItemType newType) {
        // Earlier versions are a record of what was worn: they stay as they were.
        List<Outfit> withItem = outfits.findDistinctByPiecesItemId(item.getId()).stream()
                .filter(outfit -> outfit.getReplacedBy() == null)
                .toList();
        if (withItem.isEmpty()) {
            return;
        }
        TypeChange change = planTypeChange(item, newType, withItem,
                byId(items.findByUserIdOrderByCreatedAtDesc(item.getUserId())));
        if (!change.conflicts().isEmpty()) {
            Conflict first = change.conflicts().get(0);
            int others = change.conflicts().size() - 1;
            throw new ApiException(HttpStatus.CONFLICT, "type_conflict",
                    "Can't change it to " + newType.getLabel() + ": in your outfit from " + savedOn(first.outfit())
                            + " it is " + first.from().role() + ", and that outfit already has " + first.to().one()
                            + ": " + describe(first.taking()) + "."
                            + (others == 0 ? "" : " The same goes for " + others + (others == 1 ? " more outfit." : " more outfits."))
                            + " Change the outfit first.",
                    Map.of("outfitId", first.outfit().getId()));
        }
        for (Move move : change.moves()) {
            move.outfit().getPieces().set(move.position(), new Piece(move.to(), item.getId()));
            outfits.save(move.outfit());
        }
    }

    /**
     * For every outfit with the piece: does it still fit its place with the new type, can it move to
     * the place the new type takes, or is that place taken by another piece (a conflict)? An accessory
     * place is taken only by another accessory of the same type: an outfit can have several accessories.
     */
    static TypeChange planTypeChange(Item item, ItemType newType, List<Outfit> withItem, Map<Long, Item> wardrobe) {
        List<Move> moves = new ArrayList<>();
        List<Conflict> conflicts = new ArrayList<>();
        Slot to = Slot.of(newType);
        for (Outfit outfit : withItem) {
            List<Piece> pieces = outfit.getPieces();
            for (int i = 0; i < pieces.size(); i++) {
                Piece piece = pieces.get(i);
                if (!piece.getItemId().equals(item.getId()) || piece.getSlot().accepts(newType)) {
                    continue;
                }
                Item taking = null;
                for (Piece other : pieces) {
                    Item otherItem = wardrobe.get(other.getItemId());
                    boolean samePlace = other.getSlot() == to && !other.getItemId().equals(item.getId())
                            && (to != Slot.ACCESSORY || (otherItem != null && otherItem.getType() == newType));
                    if (samePlace) {
                        taking = otherItem;
                    }
                }
                if (taking != null) {
                    conflicts.add(new Conflict(outfit, piece.getSlot(), to, taking));
                } else {
                    moves.add(new Move(outfit, i, to));
                }
            }
        }
        return new TypeChange(moves, conflicts);
    }

    /** What a piece is called in a message: its name, else its main color and type ("Navy skirt"). */
    private static String describe(Item item) {
        if (item == null) {
            return "another piece";
        }
        if (item.getName() != null && !item.getName().isBlank()) {
            return item.getName();
        }
        if (item.getType() == null) {
            return "a " + item.getCategory().name().toLowerCase(Locale.ROOT);
        }
        List<ItemColor> colors = item.getColors();
        return colors.isEmpty() ? item.getType().getLabel()
                : colors.get(0).getLabel() + " " + item.getType().getLabel().toLowerCase(Locale.ROOT);
    }

    private static String savedOn(Outfit outfit) {
        return outfit.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DAY);
    }

    /**
     * "Delete outfit": the outfit and its picture are gone for good, and so are its days in the
     * calendar, the ones worn in an earlier version of it included. The clothes stay in the wardrobe.
     */
    @Transactional
    public void delete(Long userId, Long outfitId) {
        Outfit outfit = ownCurrentOutfit(userId, outfitId);
        List<Outfit> gone = new ArrayList<>(outfits.findByReplacedBy(outfitId));
        gone.add(outfit);
        for (Outfit version : gone) {
            wears.deleteByOutfitId(version.getId());
            outfits.delete(version);
            storage.delete(version.getImageFile());
        }
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

    /** The same, for a change: an earlier version (kept for the calendar) can't be changed, only the outfit now. */
    private Outfit ownCurrentOutfit(Long userId, Long outfitId) {
        Outfit outfit = ownOutfit(userId, outfitId);
        if (outfit.getReplacedBy() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "old_version",
                    "This is how the outfit was on the days you wore it, so it can't be changed.",
                    Map.of("outfitId", outfit.getReplacedBy()));
        }
        return outfit;
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

    private static OutfitView view(Outfit outfit, List<Item> wardrobe, LocalDate lastWornOn, int wornDays) {
        Map<Long, Item> owned = byId(wardrobe);
        List<WornPiece> pieces = new ArrayList<>();
        for (Piece piece : outfit.getPieces()) {
            Item item = owned.get(piece.getItemId());
            if (item != null) {
                pieces.add(new WornPiece(piece.getSlot(), item));
            }
        }
        return new OutfitView(outfit, pieces, lastWornOn, wornDays);
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
