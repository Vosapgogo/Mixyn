package com.mixyn.app.api;

import android.content.Context;

import com.mixyn.app.BuildConfig;
import com.mixyn.app.session.SessionStore;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** One shared Retrofit client. Adds "Authorization: Bearer <token>" when the user is logged in. */
public final class ApiClient {

    private static MixynApi api;

    private ApiClient() {
    }

    /** Full address of an item photo, from the "/images/…" path the backend returns. */
    public static String imageUrl(String path) {
        return BuildConfig.API_BASE_URL + (path.startsWith("/") ? path.substring(1) : path);
    }

    public static synchronized MixynApi get(Context context) {
        if (api == null) {
            SessionStore session = new SessionStore(context.getApplicationContext());
            OkHttpClient http = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(90, TimeUnit.SECONDS) // uploads + first rembg run can be slow
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token = session.getToken();
                        if (token != null && request.header("Authorization") == null) {
                            request = request.newBuilder().header("Authorization", "Bearer " + token).build();
                        }
                        return chain.proceed(request);
                    })
                    .build();
            api = new Retrofit.Builder()
                    .baseUrl(BuildConfig.API_BASE_URL)
                    .client(http)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(MixynApi.class);
        }
        return api;
    }
}
