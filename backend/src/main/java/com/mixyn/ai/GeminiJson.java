package com.mixyn.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
import com.google.genai.types.ThinkingConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one place that talks to Gemini. It always asks for JSON that follows a schema and never throws:
 * any failure (no key, network, rate limit, bad JSON) comes back as "empty", so callers must have a fallback.
 *
 * A model that has just failed (overloaded, out of quota, too slow) is moved to the end of the list for
 * a while: on the free tier one model can stay overloaded for a long time while the others answer in a
 * few seconds, and trying it first on every request would use up the time the others need.
 */
@Component
public class GeminiJson {

    private static final Logger log = LoggerFactory.getLogger(GeminiJson.class);
    private static final String DEFAULT_MODELS = "gemini-3.1-flash-lite,gemini-3.5-flash-lite,gemini-3.5-flash,gemini-3.6-flash";
    /** How long one model may take before it counts as down. */
    private static final int MODEL_TIMEOUT_MS = 12_000;
    /** Stop trying further models once this much time has passed, so the user never waits too long. */
    private static final long TOTAL_BUDGET_MS = 20_000;
    /** How long a model that failed is tried only after the others. */
    static final long REST_MS = 10 * 60_000;

    private final Client client;
    private final List<String> models;
    /** Models that failed recently, and until when they are tried last. */
    private final Map<String, Long> restingUntil = new ConcurrentHashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();

    public GeminiJson(@Value("${mixyn.gemini.api-key}") String apiKey,
            @Value("${mixyn.gemini.model}") String model) {
        // Free-tier answers usually take 2-6 s; a model that needs more than 12 s is treated as down.
        this.client = apiKey.isBlank() ? null : Client.builder()
                .apiKey(apiKey)
                // attempts(1): the SDK must not retry on its own, or the time limit would silently multiply.
                .httpOptions(HttpOptions.builder()
                        .timeout(MODEL_TIMEOUT_MS)
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

    /**
     * @param jpeg  an optional image to show the model, or null for a text-only request
     * @param think whether the model may think before it answers. Thinking makes a flash model three
     *              times slower (8-9 s instead of 3 s for an item photo on 2026-10-16), which pays off
     *              for choosing an outfit but not for naming what a photo shows.
     */
    public Optional<JsonNode> generate(String prompt, byte[] jpeg, Schema responseSchema, boolean think) {
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
        GenerateContentConfig noThinking = config.toBuilder()
                .thinkingConfig(ThinkingConfig.builder().thinkingBudget(0).build())
                .build();

        // The free tier is sometimes overloaded ("503 high demand") or very slow for one model while
        // the others answer fine, so each model in the list gets one try until one succeeds.
        long started = System.currentTimeMillis();
        for (String model : tryOrder(models, restingUntil, started)) {
            if (System.currentTimeMillis() - started > TOTAL_BUDGET_MS) {
                break;
            }
            try {
                // Lite models don't think anyway, and they refuse a request that sets a thinking budget.
                boolean plain = think || model.contains("-lite");
                GenerateContentResponse response = client.models.generateContent(model, content,
                        plain ? config : noThinking);
                String text = response.text();
                if (text != null && !text.isBlank()) {
                    restingUntil.remove(model);
                    return Optional.of(json.readTree(text));
                }
            } catch (Exception e) {
                log.warn("Gemini model {} failed, trying it last for {} min: {}", model, REST_MS / 60_000, e.toString());
                restingUntil.put(model, System.currentTimeMillis() + REST_MS);
            }
        }
        return Optional.empty();
    }

    /**
     * The models in the order to try them now: the ones that haven't failed recently first, in the
     * configured order, then the resting ones (still a last resort if there is time left).
     */
    static List<String> tryOrder(List<String> models, Map<String, Long> restingUntil, long now) {
        List<String> ready = new ArrayList<>();
        List<String> resting = new ArrayList<>();
        for (String model : models) {
            if (restingUntil.getOrDefault(model, 0L) > now) {
                resting.add(model);
            } else {
                ready.add(model);
            }
        }
        ready.addAll(resting);
        return ready;
    }
}
