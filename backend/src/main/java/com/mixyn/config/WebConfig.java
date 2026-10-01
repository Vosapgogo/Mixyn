package com.mixyn.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mixyn.storage.ImageStorage;
import com.mixyn.user.AuthInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ImageStorage imageStorage;

    public WebConfig(AuthInterceptor authInterceptor, ImageStorage imageStorage) {
        this.authInterceptor = authInterceptor;
        this.imageStorage = imageStorage;
    }

    /** Serves the stored item photos at /images/{file}. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/**")
                .addResourceLocations(imageStorage.getDir().toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health", "/api/auth/register", "/api/auth/login", "/api/cities");
    }
}
