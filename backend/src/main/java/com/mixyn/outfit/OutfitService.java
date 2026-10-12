package com.mixyn.outfit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemRepository;
import com.mixyn.item.Labels;
import com.mixyn.outfit.Outfit.Piece;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.storage.ImageStorage;

/** Saved outfits: what the user keeps from the canvas, with a picture of the whole look. */
@Service
public class OutfitService {

    /** One piece of a saved outfit as a full item, with its place in the outfit. */
    public record WornPiece(Slot slot, Item item) {
    }

    /** A saved outfit together with its pieces, ready to be shown. */
    public record OutfitView(Outfit outfit, List<WornPiece> pieces) {
    }

    private final OutfitRepository outfits;
    private final ItemRepository items;
    private final ImageStorage storage;

    public OutfitService(OutfitRepository outfits, ItemRepository items, ImageStorage storage) {
        this.outfits = outfits;
        this.items = items;
        this.storage = storage;
    }

    /**
     * Saves the outfit on the canvas. The app sends which item is worn where and a picture of the look;
     * the ids are checked against the user's own wardrobe before anything is stored.
     *
     * @param tags    the outfit's categories, or null for none
     * @param seasons the outfit's seasons, or null for none
     */
    public OutfitView save(Long userId, LookIds look, byte[] picture, List<String> tags, List<String> seasons) {
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        List<Piece> pieces = checkLook(wardrobe, look);
        String tagList = tags == null ? null : Labels.joinTags(tags);
        String seasonList = seasons == null ? null : Labels.joinSeasons(seasons);
        Outfit outfit = new Outfit(userId, storage.save(trim(picture)), pieces);
        outfit.setTags(tagList);
        outfit.setSeason(seasonList);
        return view(outfits.save(outfit), wardrobe);
    }

    /** The user's saved outfits, newest first. */
    public List<OutfitView> list(Long userId) {
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId);
        return outfits.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(outfit -> view(outfit, wardrobe))
                .toList();
    }

    /** The user changes the categories or seasons on an outfit's card. Null fields stay as they are. */
    public OutfitView update(Long userId, Long outfitId, List<String> tags, List<String> seasons) {
        Outfit outfit = ownOutfit(userId, outfitId);
        if (tags != null) {
            outfit.setTags(Labels.joinTags(tags));
        }
        if (seasons != null) {
            outfit.setSeason(Labels.joinSeasons(seasons));
        }
        return view(outfits.save(outfit), items.findByUserIdOrderByCreatedAtDesc(userId));
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

    private static OutfitView view(Outfit outfit, List<Item> wardrobe) {
        Map<Long, Item> owned = byId(wardrobe);
        List<WornPiece> pieces = new ArrayList<>();
        for (Piece piece : outfit.getPieces()) {
            Item item = owned.get(piece.getItemId());
            if (item != null) {
                pieces.add(new WornPiece(piece.getSlot(), item));
            }
        }
        return new OutfitView(outfit, pieces);
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
