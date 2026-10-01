package com.mixyn.item;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mixyn.ai.RembgClient;
import com.mixyn.ai.TaggingService;
import com.mixyn.ai.TaggingService.Subject;
import com.mixyn.ai.TaggingService.Tags;
import com.mixyn.common.ApiException;
import com.mixyn.storage.ImageStorage;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final ItemRepository items;
    private final RembgClient rembg;
    private final ImageStorage storage;
    private final TaggingService tagging;

    public ItemService(ItemRepository items, RembgClient rembg, ImageStorage storage, TaggingService tagging) {
        this.items = items;
        this.rembg = rembg;
        this.storage = storage;
        this.tagging = tagging;
    }

    /**
     * The whole "add clothes" pipeline in one request:
     * check the file → remove the background → crop → ask the AI what it is (one item? category? color?) → save.
     * If background removal fails, or the photo isn't a single item, nothing is saved;
     * if the AI is simply unavailable the item is still saved, without tags.
     */
    public Item addItem(Long userId, byte[] photo, String filename) {
        if (!looksLikeImage(photo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_image", "This file isn't a photo we can read");
        }
        long start = System.currentTimeMillis();
        byte[] transparent = rembg.removeBackground(photo, filename == null ? "photo" : filename);
        long afterRembg = System.currentTimeMillis();
        byte[] cutout = storage.cropToItem(transparent);
        Tags tags = tagging.tag(cutout);
        long afterTagging = System.currentTimeMillis();
        rejectIfNotOneItem(tags.subject());
        String file = storage.save(cutout);
        Item item = items.save(new Item(userId, file, tags.category(), tags.colorName(), tags.colorHex()));
        log.info("Item {} added: background removal {} ms, crop + AI tagging {} ms, category {}",
                item.getId(), afterRembg - start, afterTagging - afterRembg, item.getCategory());
        return item;
    }

    /**
     * A wardrobe item is one garment on its own. If the AI saw a person, several garments or something
     * that isn't clothing, nothing is saved and the app explains what to upload instead.
     * When the AI gave no answer (subject == null) the upload is accepted: an outage must not block adding clothes.
     */
    private static void rejectIfNotOneItem(Subject subject) {
        if (subject == null || subject == Subject.SINGLE_ITEM) {
            return;
        }
        String message = switch (subject) {
            case PERSON -> "This looks like a photo of a person. Add a photo of just one clothing item, not worn by anyone.";
            case MULTIPLE_ITEMS -> "There are several items in this photo. Add one item per photo.";
            default -> "We couldn't find a clothing item in this photo. Add a photo of just one item.";
        };
        throw new ApiException(HttpStatus.BAD_REQUEST, "not_single_item", message);
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
