package com.mixyn.user;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.mixyn.common.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs before every protected /api/** request: turns the Bearer token into the current user id
 * (request attribute "userId") and makes sure /api/users/{userId}/** only reaches your own data.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID = "userId";
    public static final String TOKEN = "token";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
        Long userId = token == null ? null : authService.userIdForToken(token).orElse(null);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Please log in again");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> pathVars =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVars != null && pathVars.containsKey(USER_ID) && !pathVars.get(USER_ID).equals(userId.toString())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "This isn't your wardrobe");
        }

        request.setAttribute(USER_ID, userId);
        request.setAttribute(TOKEN, token);
        return true;
    }
}
