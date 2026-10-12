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

/**
 * Talks to the rembg container: a photo goes in, and either one model's mask or the tool's own cutout
 * comes back. CutoutService decides what to do with them.
 */
@Component
public class RembgClient {

    private final RestClient client;

    public RembgClient(@Value("${mixyn.rembg-url}") String rembgUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.client = RestClient.builder().baseUrl(rembgUrl).requestFactory(factory).build();
    }

    /** The tool's finished cutout: a PNG with the background removed, made with one model. */
    public byte[] removeBackground(byte[] photo, String filename) {
        // Pinned: rembg's default model is a 1 GB non-commercial one; u2net is baked into our image.
        return remove(photo, filename, "u2net", false);
    }

    /** Only the mask of one model: a gray PNG, white where the model sees the piece. */
    public byte[] mask(byte[] photo, String filename, String model) {
        return remove(photo, filename, model, true);
    }

    private byte[] remove(byte[] photo, String filename, String model, boolean onlyMask) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(photo) {
            @Override
            public String getFilename() {
                return filename; // rembg needs a filename to accept the part as a file
            }
        });
        form.add("model", model);
        if (onlyMask) {
            form.add("om", "true");
        }
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
