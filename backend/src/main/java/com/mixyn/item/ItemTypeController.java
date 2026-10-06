package com.mixyn.item;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The list the app shows when the user chooses an item's type (with search-as-you-type). */
@RestController
public class ItemTypeController {

    public record ItemTypeResponse(String code, String label, Category category) {
        static ItemTypeResponse from(ItemType type) {
            return new ItemTypeResponse(type.name(), type.getLabel(), type.getCategory());
        }
    }

    @GetMapping("/api/item-types")
    public List<ItemTypeResponse> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return ItemType.search(query).stream().map(ItemTypeResponse::from).toList();
    }
}
