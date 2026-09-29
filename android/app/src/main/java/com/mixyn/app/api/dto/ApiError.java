package com.mixyn.app.api.dto;

/** The backend's error body: {"error": "email_taken", "message": "..."} */
public class ApiError {
    public String error;
    public String message;
}
