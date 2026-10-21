package com.mixyn.item;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mixyn.item.ItemDtos.ItemResponse;
import com.mixyn.item.ItemDtos.ItemUpdate;
import com.mixyn.item.ItemDtos.PhotoEdit;
import com.mixyn.outfit.OutfitService.ItemUse;
import com.mixyn.user.AuthInterceptor;

/** AuthInterceptor has already checked that {userId} is the logged-in user. */
@RestController
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping("/api/users/{userId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse upload(@PathVariable Long userId, @RequestParam("photo") MultipartFile photo)
            throws IOException {
        return ItemResponse.from(itemService.addItem(userId, photo.getBytes(), photo.getOriginalFilename()));
    }

    /** These two use the item id only; the service checks the item belongs to the logged-in user. */
    @PatchMapping("/api/items/{itemId}")
    public ItemResponse update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long itemId,
            @RequestBody ItemUpdate update) {
        return ItemResponse.from(itemService.updateItem(userId, itemId, update));
    }

    @PostMapping("/api/items/{itemId}/photo")
    public ItemResponse editPhoto(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long itemId,
            @RequestBody PhotoEdit edit) {
        return ItemResponse.from(itemService.editPhoto(userId, itemId, edit));
    }

    @DeleteMapping("/api/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long itemId) {
        itemService.deleteItem(userId, itemId);
    }

    /** Where the item is worn, e.g. {"outfits": 2, "wornOn": ["2026-10-12", "2026-10-16"]}. */
    @GetMapping("/api/items/{itemId}/use")
    public ItemUse use(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long itemId) {
        return itemService.useOf(userId, itemId);
    }

    @GetMapping("/api/users/{userId}/items")
    public List<ItemResponse> list(@PathVariable Long userId) {
        return itemService.listItems(userId).stream().map(ItemResponse::from).toList();
    }
}
