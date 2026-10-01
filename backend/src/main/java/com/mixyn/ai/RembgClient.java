package com.mixyn.ai;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mixyn.common.ApiException;

/** Sends a photo to the rembg container and gets back a PNG with the background removed. */
@Component
public class RembgClient {

    private final RestClient client;

    public RembgClient(@Value("${mixyn.rembg-url}") String rembgUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(rembgUrl).requestFactory(factory).build();
    }

    public byte[] removeBackground(byte[] photo, String filename) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return filename; // rembg needs a filename to accept the part as a file
            }
        });
        // Pinned: rembg's default model is a 1 GB non-commercial one; u2net is baked into our image.
        form.add("model", "u2net");
        try {
            byte[] png = client.post().uri("/api/remove")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(byte[].class);
            if (png == null || png.length == 0) {
                throw failed();
            }
            return png;
        } catch (RestClientException e) {
            throw failed();
        }
    }

    private static ApiException failed() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "background_removal_failed",
                "We couldn't process this photo");
    }
}
