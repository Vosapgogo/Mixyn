package com.mixyn.city;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mixyn.common.ApiException;

/** City autocomplete via the free Open-Meteo Geocoding API (no key). */
@Service
public class CityService {

    public record City(String name, String region, String country, double latitude, double longitude) {
    }

    // Shape of the Open-Meteo response; only the fields we use.
    record GeoResult(String name, String admin1, String country, String feature_code,
            double latitude, double longitude) {
    }

    record GeoResponse(List<GeoResult> results) {
    }

    /** A city search that takes longer than this counts as failed, so the sign-up form never hangs. */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final RestClient client = RestClient.builder()
            .baseUrl("https://geocoding-api.open-meteo.com")
            .requestFactory(withTimeout())
            .build();

    private static JdkClientHttpRequestFactory withTimeout() {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return factory;
    }

    public List<City> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            return List.of();
        }
        try {
            GeoResponse response = client.get()
                    .uri(uri -> uri.path("/v1/search")
                            .queryParam("name", q)
                            .queryParam("count", 15)
                            // Open-Meteo matches names in the requested language, so Cyrillic input needs "uk".
                            .queryParam("language", isCyrillic(q) ? "uk" : "en")
                            .build())
                    .retrieve()
                    .body(GeoResponse.class);
            if (response == null || response.results() == null) {
                return List.of();
            }
            Set<String> seen = new HashSet<>();
            return response.results().stream()
                    // Only populated places (PPL, PPLA, PPLC…), not airports or regions.
                    .filter(r -> r.feature_code() != null && r.feature_code().startsWith("PPL"))
                    .map(r -> new City(r.name(), r.admin1(), r.country(), r.latitude(), r.longitude()))
                    // The same city can appear several times (city + its municipality); keep the first.
                    .filter(c -> seen.add(c.name() + "|" + c.region() + "|" + c.country()))
                    .limit(8)
                    .toList();
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "city_search_failed", "Can't load cities right now");
        }
    }

    private static boolean isCyrillic(String text) {
        return text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC);
    }
}
