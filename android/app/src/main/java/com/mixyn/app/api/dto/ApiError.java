package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

/**
 * The backend's error body: {"error": "email_taken", "message": "..."}. A conflict about a saved outfit
 * also says which one: {"error": "duplicate_outfit", "message": "...", "outfitId": 5}.
 */
public class ApiError {
    public String error;
    public String message;
    @Nullable
    public Long outfitId;
}
