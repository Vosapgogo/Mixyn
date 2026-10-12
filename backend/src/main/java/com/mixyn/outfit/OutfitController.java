package com.mixyn.outfit;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mixyn.outfit.OutfitDtos.GenerateRequest;
import com.mixyn.outfit.OutfitDtos.GenerateResponse;
import com.mixyn.outfit.OutfitDtos.OutfitResponse;
import com.mixyn.outfit.OutfitDtos.SaveRequest;

import jakarta.validation.Valid;

/** AuthInterceptor has already checked that {userId} is the logged-in user. */
@RestController
public class OutfitController {

    private final StylistService stylist;
    private final OutfitService outfits;

    public OutfitController(StylistService stylist, OutfitService outfits) {
        this.stylist = stylist;
        this.outfits = outfits;
    }

    @PostMapping("/api/users/{userId}/outfits/generate")
    public GenerateResponse generate(@PathVariable Long userId,
            @Valid @RequestBody(required = false) GenerateRequest request) {
        if (request == null) {
            return GenerateResponse.from(stylist.generate(userId, null, null, null));
        }
        return GenerateResponse.from(
                stylist.generate(userId, request.hint(), request.history(), request.current()));
    }

    /** A multipart request with two parts: "picture" (the look as a PNG) and "outfit" (JSON). */
    @PostMapping("/api/users/{userId}/outfits")
    @ResponseStatus(HttpStatus.CREATED)
    public OutfitResponse save(@PathVariable Long userId, @RequestPart("picture") MultipartFile picture,
            @Valid @RequestPart("outfit") SaveRequest request) throws IOException {
        return OutfitResponse.from(
                outfits.save(userId, request.look(), picture.getBytes(), request.tags(), request.seasons()));
    }

    @GetMapping("/api/users/{userId}/outfits")
    public List<OutfitResponse> list(@PathVariable Long userId) {
        return outfits.list(userId).stream().map(OutfitResponse::from).toList();
    }
}
