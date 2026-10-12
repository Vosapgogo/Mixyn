package com.mixyn.outfit;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mixyn.outfit.OutfitDtos.DayResponse;
import com.mixyn.outfit.OutfitDtos.GenerateRequest;
import com.mixyn.outfit.OutfitDtos.GenerateResponse;
import com.mixyn.outfit.OutfitDtos.OutfitResponse;
import com.mixyn.outfit.OutfitDtos.OutfitUpdate;
import com.mixyn.outfit.OutfitDtos.SaveRequest;
import com.mixyn.outfit.OutfitDtos.WearRequest;
import com.mixyn.user.AuthInterceptor;

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
                outfits.save(userId, request.look(), picture.getBytes(), request.tags(), request.seasons(),
                        request.wornOn()));
    }

    @GetMapping("/api/users/{userId}/outfits")
    public List<OutfitResponse> list(@PathVariable Long userId) {
        return outfits.list(userId).stream().map(OutfitResponse::from).toList();
    }

    /** Uses the outfit id only; the service checks the outfit belongs to the logged-in user. */
    @PatchMapping("/api/outfits/{outfitId}")
    public OutfitResponse update(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long outfitId,
            @Valid @RequestBody OutfitUpdate update) {
        return OutfitResponse.from(outfits.update(userId, outfitId, update.tags(), update.seasons()));
    }

    @DeleteMapping("/api/outfits/{outfitId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthInterceptor.USER_ID) Long userId, @PathVariable Long outfitId) {
        outfits.delete(userId, outfitId);
    }

    /** "Wear it today" on a saved outfit. The date is the user's own today, e.g. 2026-10-12. */
    @PutMapping("/api/users/{userId}/calendar/{date}")
    public DayResponse wear(@PathVariable Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody WearRequest request) {
        return DayResponse.from(outfits.wear(userId, request.outfitId(), date));
    }

    /** The outfit calendar between two days (both included): only the days something was worn. */
    @GetMapping("/api/users/{userId}/calendar")
    public List<DayResponse> calendar(@PathVariable Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return outfits.calendar(userId, from, to).stream().map(DayResponse::from).toList();
    }
}
