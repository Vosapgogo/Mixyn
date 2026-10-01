package com.mixyn.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place that talks to Gemini. It always asks for JSON that follows a schema and never throws:
 * any failure (no key, network, rate limit, bad JSON) comes back as "empty", so callers must have a fallback.
 */
@Component
public class GeminiJson {

    private static final Logger log = LoggerFactory.getLogger(GeminiJson.class);

    private final Client client;
    private final String model;
    private final JsonMapper json = JsonMapper.builder().build();

    public GeminiJson(@Value("${mixyn.gemini.api-key}") String apiKey,
            @Value("${mixyn.gemini.model}") String model) {
        this.client = apiKey.isBlank() ? null : Client.builder().apiKey(apiKey).build();
        this.model = model.isBlank() ? "gemini-3.5-flash" : model;
        if (client == null) {
            log.warn("GEMINI_API_KEY is not set: AI tagging and the AI stylist will use their fallbacks");
        }
    }

    /** @param jpeg an optional image to show the model, or null for a text-only request */
    public Optional<JsonNode> generate(String prompt, byte[] jpeg, Schema responseSchema) {
        if (client == null) {
            return Optional.empty();
        }
        List<Part> parts = new ArrayList<>();
        parts.add(Part.fromText(prompt));
        if (jpeg != null) {
            parts.add(Part.fromBytes(jpeg, "image/jpeg"));
        }
        Content content = Content.fromParts(parts.toArray(Part[]::new));
        GenerateContentConfig config = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(responseSchema)
                .build();

        // The free tier sometimes answers "503 high demand" for a moment, so try twice.
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                GenerateContentResponse response = client.models.generateContent(model, content, config);
                String text = response.text();
                return text == null || text.isBlank() ? Optional.empty() : Optional.of(json.readTree(text));
            } catch (Exception e) {
                log.warn("Gemini request failed (attempt {}): {}", attempt, e.toString());
                if (attempt == 1) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                }
            }
        }
        return Optional.empty();
    }
}
