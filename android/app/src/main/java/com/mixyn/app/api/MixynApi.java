package com.mixyn.app.api;

import com.mixyn.app.api.dto.AuthResponse;
import com.mixyn.app.api.dto.CalendarDayDto;
import com.mixyn.app.api.dto.ChatReply;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.ItemTypeDto;
import com.mixyn.app.api.dto.LoginRequest;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.api.dto.RegisterRequest;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.api.dto.WeatherDto;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/** Every backend endpoint the app calls. */
public interface MixynApi {

    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest request);

    /** Takes the token explicitly: the app clears its session before this request is sent. */
    @POST("api/auth/logout")
    Call<Void> logout(@Header("Authorization") String bearerToken);

    @GET("api/me")
    Call<UserDto> me();

    @GET("api/cities")
    Call<List<CityDto>> cities(@Query("q") String query);

    /** Uploads one photo; the backend removes the background and tags it. Can take several seconds. */
    @Multipart
    @POST("api/users/{userId}/items")
    Call<ItemDto> uploadItem(@Path("userId") long userId, @Part MultipartBody.Part photo);

    @GET("api/users/{userId}/items")
    Call<List<ItemDto>> items(@Path("userId") long userId);

    /** Changes only the fields present in the map: type, colors, name, tags, seasons. */
    @PATCH("api/items/{itemId}")
    Call<ItemDto> updateItem(@Path("itemId") long itemId, @Body Map<String, Object> changes);

    /** "Edit photo" → Done; body is {"rotation": 0|90|180|270, "mirror": false, "scale": 50..150}. */
    @POST("api/items/{itemId}/photo")
    Call<ItemDto> editPhoto(@Path("itemId") long itemId, @Body Map<String, Object> edit);

    /** Removes the item from the wardrobe for good, picture included. */
    @DELETE("api/items/{itemId}")
    Call<Void> deleteItem(@Path("itemId") long itemId);

    /**
     * Asks the AI stylist for a look from the user's own items. Body: {"hint": "...", "history": [...],
     * "current": {...}} — all optional; history and current give the stylist its memory of this session.
     */
    @POST("api/users/{userId}/outfits/generate")
    Call<GenerateResult> generateOutfit(@Path("userId") long userId, @Body Map<String, Object> body);

    /** The Stylist chat: {message, history: [{role, text, looks, itemIds}]} → the stylist's answer. */
    @POST("api/users/{userId}/stylist/chat")
    Call<ChatReply> stylistChat(@Path("userId") long userId, @Body Map<String, Object> body);

    /**
     * Saves the outfit on the canvas. Two parts: "picture", the look as a PNG, and "outfit", sent as
     * JSON: {"look": {topId…}, "tags": [...], "seasons": [...], "wornOn": "2026-10-12"}. Only "look" is
     * required; with "wornOn" the new outfit is also put on that day of the calendar.
     */
    @Multipart
    @POST("api/users/{userId}/outfits")
    Call<OutfitDto> saveOutfit(@Path("userId") long userId, @Part MultipartBody.Part picture,
            @Part("outfit") Map<String, Object> outfit);

    /**
     * "Update" after editing a saved outfit on the canvas: the same two parts as saveOutfit (without
     * "wornOn"). The outfit keeps its id and its days in the calendar.
     */
    @Multipart
    @PUT("api/outfits/{outfitId}")
    Call<OutfitDto> replaceOutfit(@Path("outfitId") long outfitId, @Part MultipartBody.Part picture,
            @Part("outfit") Map<String, Object> outfit);

    /** The saved outfits, newest first. */
    @GET("api/users/{userId}/outfits")
    Call<List<OutfitDto>> outfits(@Path("userId") long userId);

    /** Changes only the fields present in the map: tags, seasons. */
    @PATCH("api/outfits/{outfitId}")
    Call<OutfitDto> updateOutfit(@Path("outfitId") long outfitId, @Body Map<String, Object> changes);

    /** Deletes a saved outfit for good, with its picture and its days in the calendar. */
    @DELETE("api/outfits/{outfitId}")
    Call<Void> deleteOutfit(@Path("outfitId") long outfitId);

    /** "Wear it today" on a saved outfit; body is {"outfitId": 5}, the date is like "2026-10-12". */
    @PUT("api/users/{userId}/calendar/{date}")
    Call<CalendarDayDto> wearOutfit(@Path("userId") long userId, @Path("date") String date,
            @Body Map<String, Object> body);

    /** The days between two dates (both included) on which an outfit was worn. */
    @GET("api/users/{userId}/calendar")
    Call<List<CalendarDayDto>> calendar(@Path("userId") long userId, @Query("from") String from,
            @Query("to") String to);

    /** Current weather in the logged-in user's city. */
    @GET("api/me/weather")
    Call<WeatherDto> weather();

    /** The fixed color palette. */
    @GET("api/item-colors")
    Call<List<ColorDto>> itemColors();

    /** The fixed list of clothing types (Blazer, Jeans…). */
    @GET("api/item-types")
    Call<List<ItemTypeDto>> itemTypes();
}
