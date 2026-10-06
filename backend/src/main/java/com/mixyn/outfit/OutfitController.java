package com.mixyn.outfit;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mixyn.outfit.OutfitDtos.GenerateRequest;
import com.mixyn.outfit.OutfitDtos.GenerateResponse;

import jakarta.validation.Valid;

/** AuthInterceptor has already checked that {userId} is the logged-in user. */
@RestController
public class OutfitController {

    private final StylistService stylist;

    public OutfitController(StylistService stylist) {
        this.stylist = stylist;
    }

    @PostMapping("/api/users/{userId}/outfits/generate")
    public GenerateResponse generate(@PathVariable Long userId,
            @Valid @RequestBody(required = false) GenerateRequest request) {
        return GenerateResponse.from(stylist.generate(userId, request == null ? null : request.hint()));
    }
}
