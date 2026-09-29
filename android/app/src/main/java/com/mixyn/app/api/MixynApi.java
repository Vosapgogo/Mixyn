package com.mixyn.app.api;

import com.mixyn.app.api.dto.AuthResponse;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.api.dto.LoginRequest;
import com.mixyn.app.api.dto.RegisterRequest;
import com.mixyn.app.api.dto.UserDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
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
}
