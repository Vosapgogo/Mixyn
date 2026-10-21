package com.mixyn.item;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mixyn.ai.CutoutService;
import com.mixyn.ai.TaggingService;
import com.mixyn.ai.TaggingService.Box;
import com.mixyn.ai.TaggingService.Opening;
import com.mixyn.ai.TaggingService.Subject;
import com.mixyn.ai.TaggingService.Tags;
import com.mixyn.common.ApiException;
import com.mixyn.item.ItemDtos.ItemUpdate;
import com.mixyn.item.ItemDtos.PhotoEdit;
import com.mixyn.outfit.OutfitService;
import com.mixyn.outfit.OutfitService.ItemUse;
import com.mixyn.storage.ImageStorage;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);
    private static final int MIN_SCALE = 50;
    private static final int MAX_SCALE = 150;

    private final ItemRepository items;
    private final CutoutService cutouts;
    private final ImageStorage storage;
    private final TaggingService tagging;
    private final OutfitService outfits;

    public ItemService(ItemRepository items, CutoutService cutouts, ImageStorage storage, TaggingService tagging,
            OutfitService outfits) {
        this.items = items;
        this.cutouts = cutouts;
        this.storage = storage;
        this.tagging = tagging;
        this.outfits = outfits;
    }

    /**
     * The whole "add clothes" pipeline in one request:
     * check the file → remove the background → crop → ask the AI what it is (one item? category? color?) → save.
     * If background removal fails, or the photo isn't about a single item, nothing is saved;
     * if the AI is simply unavailable the item is still saved, without tags.
     * A piece photographed on a person (a headscarf on a model) is accepted: background removal keeps
     * the person too, so the cutout is then cut down to the box the AI drew around the piece.
     */
    public Item addItem(Long userId, byte[] photo, String filename) {
        if (!looksLikeImage(photo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_image", "This file isn't a photo we can read");
        }
        long start = System.currentTimeMillis();
        byte[] transparent = cutouts.cutOut(photo, filename == null ? "photo" : filename);
        long afterRembg = System.currentTimeMillis();
        byte[] cutout = storage.cropToItem(transparent);
        Tags tags = tagging.tag(cutout);
        long afterTagging = System.currentTimeMillis();
        rejectIfNotOneItem(tags.subject());
        boolean cutToBox = tags.onBody() && tags.box() != null;
        if (cutToBox) {
            Box box = tags.box();
            cutout = storage.cropToItem(
                    storage.cropToBox(cutout, box.top(), box.left(), box.bottom(), box.right(), Box.SCALE));
        }
        String file = storage.save(cutout);
        Item item = new Item(userId, file, tags.type(), tags.category(), tags.colors());
        // The opening was measured on the picture the AI saw, so it is dropped if that picture was cut again.
        if (tags.opening() != null && !cutToBox && canBeOpen(item)) {
            item.setOpening(tags.opening().numbers());
        }
        item = items.save(item);
        log.info("Item {} added: background removal {} ms, crop + AI tagging {} ms, type {}, category {}",
                item.getId(), afterRembg - start, afterTagging - afterRembg, item.getType(), item.getCategory());
        return item;
    }

    /**
     * A wardrobe item is one piece. If the AI saw a whole outfit on a person, several separate pieces or
     * something that isn't clothing, nothing is saved and the app explains what to upload instead.
     * When the AI gave no answer (subject == null) the upload is accepted: an outage must not block adding clothes.
     */
    private static void rejectIfNotOneItem(Subject subject) {
        if (subject == null || subject == Subject.SINGLE_ITEM) {
            return;
        }
        String message = switch (subject) {
            case PERSON -> "This photo shows a whole outfit on a person. Add a photo where one piece is the clear subject.";
            case MULTIPLE_ITEMS -> "There are several pieces in this photo. Add one piece per photo.";
            default -> "We couldn't find a clothing item in this photo. Add a photo of just one item.";
        };
        throw new ApiException(HttpStatus.BAD_REQUEST, "not_single_item", message);
    }

    /** Only tops and outerwear have a front that can be worn open. */
    private static boolean canBeOpen(Item item) {
        return item.fits(Category.TOP) || item.fits(Category.OUTERWEAR);
    }

    /**
     * The user corrects or enriches an item on its card. Null fields in the update are left as they are.
     * A new type also moves the piece in the saved outfits that have it, in the same transaction.
     */
    @Transactional
    public Item updateItem(Long userId, Long itemId, ItemUpdate update) {
        Item item = ownItem(userId, itemId);
        if (update.type() != null) {
            ItemType type = ItemType.fromCode(update.type());
            if (type == null) {
                throw invalid("Choose a type from the list");
            }
            if (type != item.getType()) {
                outfits.fitTypeChange(item, type); // may refuse: a saved outfit has no room for the new kind
            }
            item.setType(type);
            if (!canBeOpen(item)) {
                item.setOpening(null); // trousers have no front to wear open
            }
        }
        if (update.openFront() != null) {
            if (update.openFront() && !canBeOpen(item)) {
                throw invalid("Only tops and outerwear can be worn open");
            }
            if (!update.openFront()) {
                item.setOpening(null);
            } else if (item.getOpening().isEmpty()) {
                // Nobody measured where this piece opens, so it gets a typical opening.
                item.setOpening(Opening.TYPICAL.numbers());
            }
        }
        if (update.colors() != null) {
            List<ItemColor> colors = ItemColor.fromCodes(update.colors());
            // Every code must be a palette color, with no repeats and no more than the limit.
            if (colors.size() != update.colors().size()) {
                throw invalid("Choose up to " + ItemColor.MAX_PER_ITEM + " colors from the list");
            }
            item.setColors(colors);
        }
        if (update.name() != null) {
            String name = update.name().trim();
            if (name.length() > 60) {
                throw invalid("The name is too long (max 60 characters)");
            }
            item.setName(name.isEmpty() ? null : name); // an empty name clears it
        }
        if (update.tags() != null) {
            item.setTags(Labels.joinTags(update.tags()));
        }
        if (update.seasons() != null) {
            item.setSeason(Labels.joinSeasons(update.seasons()));
        }
        return items.save(item);
    }

    /**
     * "Edit photo": rotate and/or mirror the stored picture and set its display size.
     * A changed picture gets a new file name so apps don't keep showing a cached old one.
     */
    public Item editPhoto(Long userId, Long itemId, PhotoEdit edit) {
        if (edit.rotation() % 90 != 0 || edit.rotation() < 0 || edit.rotation() > 270) {
            throw invalid("Rotate by 0, 90, 180 or 270 degrees");
        }
        if (edit.scale() < MIN_SCALE || edit.scale() > MAX_SCALE) {
            throw invalid("Size must be between " + MIN_SCALE + "% and " + MAX_SCALE + "%");
        }
        Item item = ownItem(userId, itemId);
        item.setScale(edit.scale());
        String oldFile = null;
        if (edit.rotation() != 0 || edit.mirror()) {
            oldFile = item.getImageFile();
            // Mirror first, then turn: the same order the app uses for its live preview.
            byte[] png = storage.read(oldFile);
            if (edit.mirror()) {
                png = storage.mirror(png);
            }
            item.setImageFile(storage.save(storage.rotate(png, edit.rotation())));
            // The opening of an open-front piece is measured across the upright picture.
            List<Integer> opening = item.getOpening();
            if (edit.rotation() != 0) {
                item.setOpening(null);
            } else if (!opening.isEmpty()) {
                item.setOpening(List.of(Box.SCALE - opening.get(1), Box.SCALE - opening.get(0),
                        Box.SCALE - opening.get(3), Box.SCALE - opening.get(2)));
            }
        }
        Item saved = items.save(item);
        if (oldFile != null) {
            storage.delete(oldFile);
        }
        return saved;
    }

    /**
     * "Remove from wardrobe": the item and its picture are gone for good. Saved outfits that had the
     * piece lose it in the same transaction, so no outfit is left pointing at an item that isn't there.
     */
    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        Item item = ownItem(userId, itemId);
        outfits.forgetItem(itemId);
        items.delete(item);
        storage.delete(item.getImageFile());
    }

    /** Where the user's item is worn: asked before "Remove from wardrobe", which leaves the outfits' pictures as they are. */
    public ItemUse useOf(Long userId, Long itemId) {
        return outfits.useOf(ownItem(userId, itemId).getId());
    }

    /** Someone else's item is reported as "not found", so ids can't be probed. */
    private Item ownItem(Long userId, Long itemId) {
        return items.findById(itemId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Item not found"));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }

    public List<Item> listItems(Long userId) {
        return items.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** JPEG, PNG, WebP or HEIC/HEIF, recognized by the first bytes of the file (not by its name). */
    private static boolean looksLikeImage(byte[] b) {
        if (b == null || b.length < 12) {
            return false;
        }
        boolean jpeg = (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
        boolean png = (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E';
        boolean heif = b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p';
        return jpeg || png || webp || heif;
    }
}
