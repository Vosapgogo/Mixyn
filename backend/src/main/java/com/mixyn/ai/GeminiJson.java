package com.mixyn.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 * On the free tier the models' speed changes by the hour: at 16:00 on 2026-10-16 the flash models took
 * 18-27 s for an item photo while a lite model answered in 4 s, and an hour earlier it was the other way
 * round. So the models are not tried strictly one after the other. The first one is asked; if it hasn't
 * answered after a few seconds (or has failed), the next one is asked as well, without waiting for the
 * first; the first valid answer wins. A model that failed or was outrun is tried last for a while, so
 * the next request starts with the one that answered.
 */
@Component
public class GeminiJson {

    private static final Logger log = LoggerFactory.getLogger(GeminiJson.class);
    private static final String DEFAULT_MODELS = "gemini-3.6-flash,gemini-3.5-flash,gemini-3.5-flash-lite,gemini-3.1-flash-lite";
    /** How long one model may take before it counts as down. */
    private static final int MODEL_TIMEOUT_MS = 12_000;
    /** Stop trying further models once this much time has passed, so the user never waits too long. */
    private static final long TOTAL_BUDGET_MS = 20_000;
    /** How long a model that failed is tried only after the others. */
    static final long REST_MS = 10 * 60_000;
    /**
     * How long one model has before the next one is asked as well. A healthy flash model names a photo
     * in 2-4 s without thinking, and needs 8-9 s to choose an outfit with thinking.
     */
    private static final long HEDGE_MS = 5_000;
    private static final long HEDGE_THINKING_MS = 10_000;

    private final Client client;
    private final List<String> models;
    /** Models that failed recently, and until when they are tried last. */
    private final Map<String, Long> restingUntil = new ConcurrentHashMap<>();
    /** The requests run here, so several models can be asked at once; an answer that comes too late is ignored. */
    private final ExecutorService calls = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "gemini");
        thread.setDaemon(true);
        return thread;
    });
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

        long started = System.currentTimeMillis();
        Race race = firstAnswer(tryOrder(models, restingUntil, started), model -> {
            // Lite models don't think anyway, and they refuse a request that sets a thinking budget.
            boolean plain = think || model.contains("-lite");
            GenerateContentResponse response = client.models.generateContent(model, content,
                    plain ? config : noThinking);
            String text = response.text();
            return text == null || text.isBlank() ? null : json.readTree(text);
        }, calls, think ? HEDGE_THINKING_MS : HEDGE_MS, TOTAL_BUDGET_MS);

        long until = System.currentTimeMillis() + REST_MS;
        race.failed().forEach((model, reason) -> {
            log.warn("Gemini model {} failed, trying it last for {} min: {}", model, REST_MS / 60_000, reason);
            restingUntil.put(model, until);
        });
        for (String model : race.outrun()) {
            log.info("Gemini model {} was too slow, trying it last for {} min", model, REST_MS / 60_000);
            restingUntil.put(model, until);
        }
        if (race.winner() != null) {
            restingUntil.remove(race.winner());
            log.info("Gemini model {} answered in {} ms", race.winner(), System.currentTimeMillis() - started);
        }
        return Optional.ofNullable(race.answer());
    }

    /** One request to one model: the parsed answer, or null for an empty one. May throw on any failure. */
    interface ModelCall {
        JsonNode ask(String model) throws Exception;
    }

    /**
     * How a race between the models went: the answer and who gave it (null, null if nobody did in time),
     * the models that failed (with why) and the ones still running when it ended.
     */
    record Race(JsonNode answer, String winner, Map<String, String> failed, List<String> outrun) {
    }

    /**
     * Asks the models in this order, starting the next one whenever the ones asked so far have all failed
     * or none has answered for hedgeMs, and returns the first valid answer. Gives up after budgetMs.
     * Requests still running then (or when another model wins) are left to finish on their own; their
     * answers are not used, and their models count as outrun.
     */
    static Race firstAnswer(List<String> order, ModelCall call, ExecutorService pool, long hedgeMs, long budgetMs) {
        CompletionService<Object[]> done = new ExecutorCompletionService<>(pool);
        Set<String> running = new LinkedHashSet<>();
        Map<String, String> failed = new LinkedHashMap<>();
        long deadline = System.currentTimeMillis() + budgetMs;
        int next = 0;
        while (true) {
            long now = System.currentTimeMillis();
            if (now >= deadline) {
                break;
            }
            if (running.isEmpty()) {
                if (next == order.size()) {
                    break; // every model has failed
                }
                String model = order.get(next++);
                running.add(model);
                ask(done, call, model);
                continue;
            }
            Future<Object[]> finished;
            try {
                finished = done.poll(Math.min(hedgeMs, deadline - now), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (finished == null) {
                // Nobody has answered for a while: ask the next model too, and keep waiting for all of them.
                if (next < order.size()) {
                    String model = order.get(next++);
                    running.add(model);
                    ask(done, call, model);
                }
                continue;
            }
            Object[] result;
            try {
                result = finished.get();
            } catch (ExecutionException e) {
                ModelFailure failure = (ModelFailure) e.getCause();
                running.remove(failure.model);
                failed.put(failure.model, failure.getCause().toString());
                continue;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String model = (String) result[0];
            running.remove(model);
            if (result[1] instanceof JsonNode answer) {
                return new Race(answer, model, failed, new ArrayList<>(running));
            }
            failed.put(model, "an empty answer");
        }
        return new Race(null, null, failed, new ArrayList<>(running));
    }

    /** Sends one model its request on the pool; a failure comes back saying which model it was. */
    private static void ask(CompletionService<Object[]> done, ModelCall call, String model) {
        done.submit(() -> {
            try {
                return new Object[] {model, call.ask(model)};
            } catch (Exception e) {
                throw new ModelFailure(model, e);
            }
        });
    }

    /** A failed request, with the model it was sent to. */
    static final class ModelFailure extends Exception {
        final String model;

        ModelFailure(String model, Exception cause) {
            super(model + ": " + cause, cause);
            this.model = model;
        }
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
