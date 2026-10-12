package com.mixyn.item;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The fixed lists the app shows when the user chooses an item's type (with search-as-you-type) or colors. */
@RestController
public class ItemTypeController {

    public record ItemTypeResponse(String code, String label, Category category, Category alsoCategory) {
        static ItemTypeResponse from(ItemType type) {
            return new ItemTypeResponse(type.name(), type.getLabel(), type.getCategory(), type.getAlsoCategory());
        }
    }

    public record ItemColorResponse(String code, String label, String hex) {
        static ItemColorResponse from(ItemColor color) {
            return new ItemColorResponse(color.name(), color.getLabel(), color.getHex());
        }
    }

    @GetMapping("/api/item-colors")
    public List<ItemColorResponse> colors() {
        return Arrays.stream(ItemColor.values()).map(ItemColorResponse::from).toList();
    }

    @GetMapping("/api/item-types")
    public List<ItemTypeResponse> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return ItemType.search(query).stream().map(ItemTypeResponse::from).toList();
    }
}
