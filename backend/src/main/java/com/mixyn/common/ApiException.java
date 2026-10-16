package com.mixyn.common;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * An error the API reports to the app as {"error": code, "message": text}, plus, for some errors, a
 * few details the app can act on (the outfit a conflict is about: {"outfitId": 5}).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
