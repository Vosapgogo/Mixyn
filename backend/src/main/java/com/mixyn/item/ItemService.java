package com.mixyn.item;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mixyn.ai.RembgClient;
import com.mixyn.ai.TaggingService;
import com.mixyn.ai.TaggingService.Tags;
import com.mixyn.common.ApiException;
import com.mixyn.storage.ImageStorage;

@Service
public class ItemService {

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
     * check the file → remove the background → crop → ask the AI for category and color → save.
     * If background removal fails nothing is saved; if only the AI tagging fails the item is still saved.
     */
    public Item addItem(Long userId, byte[] photo, String filename) {
        if (!looksLikeImage(photo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_image", "This file isn't a photo we can read");
        }
        byte[] transparent = rembg.removeBackground(photo, filename == null ? "photo" : filename);
        byte[] cutout = storage.cropToItem(transparent);
        Tags tags = tagging.tag(cutout);
        String file = storage.save(cutout);
        return items.save(new Item(userId, file, tags.category(), tags.colorName(), tags.colorHex()));
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
