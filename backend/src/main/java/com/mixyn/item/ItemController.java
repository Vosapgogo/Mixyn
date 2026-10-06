package com.mixyn.item;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
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
import com.mixyn.item.ItemDtos.RotateRequest;
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

    @PostMapping("/api/items/{itemId}/rotate")
    public ItemResponse rotate(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long itemId,
            @RequestBody RotateRequest request) {
        return ItemResponse.from(itemService.rotateItem(userId, itemId, request.degrees()));
    }

    @GetMapping("/api/users/{userId}/items")
    public List<ItemResponse> list(@PathVariable Long userId) {
        return itemService.listItems(userId).stream().map(ItemResponse::from).toList();
    }
}
