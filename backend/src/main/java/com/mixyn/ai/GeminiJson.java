package com.mixyn.ai;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
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
    private static final String DEFAULT_MODELS = "gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-3.5-flash,gemini-3.6-flash";
    /** Stop trying further models once this much time has passed, so the user never waits too long. */
    private static final long TOTAL_BUDGET_MS = 14_000;

    private final Client client;
    private final List<String> models;
    private final JsonMapper json = JsonMapper.builder().build();

    public GeminiJson(@Value("${mixyn.gemini.api-key}") String apiKey,
            @Value("${mixyn.gemini.model}") String model) {
        // Free-tier answers usually take 2-5 s; a model that needs more than 8 s is treated as down.
        this.client = apiKey.isBlank() ? null : Client.builder()
                .apiKey(apiKey)
                // attempts(1): the SDK must not retry on its own, or the time limit would silently multiply.
                .httpOptions(HttpOptions.builder()
                        .timeout(8_000)
                        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                        .build())
                .build();
        // GEMINI_MODEL may list several models: "primary,fallback1,fallback2".
        this.models = Arrays.stream((model.isBlank() ? DEFAULT_MODELS : model).split(","))
                .map(String::trim).filter(m -> !m.isEmpty()).toList();
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

        // The free tier is sometimes overloaded ("503 high demand") or very slow for one model while
        // the others answer fine, so each model in the list gets one try until one succeeds.
        long started = System.currentTimeMillis();
        for (String model : models) {
            if (System.currentTimeMillis() - started > TOTAL_BUDGET_MS) {
                break;
            }
            try {
                GenerateContentResponse response = client.models.generateContent(model, content, config);
                String text = response.text();
                if (text != null && !text.isBlank()) {
                    return Optional.of(json.readTree(text));
                }
            } catch (Exception e) {
                log.warn("Gemini model {} failed: {}", model, e.toString());
            }
        }
        return Optional.empty();
    }
}
