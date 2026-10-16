package com.mixyn.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

/**
 * The order in which the models are tried (one that failed or was outrun a moment ago waits at the back
 * for a while), and the race between them: a slow model doesn't keep the others from answering.
 */
class GeminiJsonTest {

    private static final List<String> MODELS = List.of("lite-a", "lite-b", "flash");
    private static final long NOW = 1_000_000L;

    @Test
    void triesTheModelsInTheConfiguredOrderWhenNoneHasFailed() {
        assertEquals(MODELS, GeminiJson.tryOrder(MODELS, Map.of(), NOW));
    }

    @Test
    void triesAModelThatJustFailedLast() {
        Map<String, Long> resting = Map.of("lite-a", NOW + GeminiJson.REST_MS);

        assertEquals(List.of("lite-b", "flash", "lite-a"), GeminiJson.tryOrder(MODELS, resting, NOW));
    }

    @Test
    void keepsTheConfiguredOrderAmongTheRestingModels() {
        Map<String, Long> resting = Map.of("flash", NOW + 5, "lite-a", NOW + 10);

        assertEquals(List.of("lite-b", "lite-a", "flash"), GeminiJson.tryOrder(MODELS, resting, NOW));
    }

    // ----- The race between the models -----

    private static final long HEDGE = 100;
    private static final long BUDGET = 1_000;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    /** A pretend model: answers after this many milliseconds, or fails, and remembers that it was asked. */
    private GeminiJson.ModelCall models(Map<String, Long> answersAfter, List<String> asked) {
        return model -> {
            asked.add(model);
            long wait = answersAfter.get(model);
            if (wait < 0) {
                throw new IllegalStateException("503 high demand");
            }
            Thread.sleep(wait);
            return JSON.readTree("{\"model\":\"" + model + "\"}");
        };
    }

    @Test
    void aQuickFirstModelIsTheOnlyOneAsked() {
        List<String> asked = new CopyOnWriteArrayList<>();
        GeminiJson.Race race = GeminiJson.firstAnswer(MODELS,
                models(Map.of("lite-a", 10L, "lite-b", 10L, "flash", 10L), asked), pool, HEDGE, BUDGET);

        assertEquals("lite-a", race.winner());
        assertEquals(List.of("lite-a"), asked);
    }

    @Test
    void aSlowFirstModelIsOutrunByTheNextOne() {
        List<String> asked = new CopyOnWriteArrayList<>();
        GeminiJson.Race race = GeminiJson.firstAnswer(MODELS,
                models(Map.of("lite-a", 800L, "lite-b", 20L, "flash", 20L), asked), pool, HEDGE, BUDGET);

        assertEquals("lite-b", race.winner());
        assertEquals("{\"model\":\"lite-b\"}", race.answer().toString()); // its answer, not lite-a's
        assertEquals(List.of("lite-a"), race.outrun());
        assertEquals(List.of("lite-a", "lite-b"), asked); // flash was never needed
    }

    @Test
    void aFailureBringsTheNextModelInAtOnce() {
        List<String> asked = new CopyOnWriteArrayList<>();
        long longHedge = 2_000;
        long started = System.currentTimeMillis();
        GeminiJson.Race race = GeminiJson.firstAnswer(MODELS,
                models(Map.of("lite-a", -1L, "lite-b", 10L, "flash", 10L), asked), pool, longHedge, 5_000);

        assertEquals("lite-b", race.winner());
        assertTrue(race.failed().containsKey("lite-a"));
        assertTrue(System.currentTimeMillis() - started < longHedge, "after a failure the next model is asked at once");
    }

    @Test
    void givesUpWhenNobodyAnswersInTime() {
        List<String> asked = new CopyOnWriteArrayList<>();
        GeminiJson.Race race = GeminiJson.firstAnswer(MODELS,
                models(Map.of("lite-a", 5_000L, "lite-b", 5_000L, "flash", 5_000L), asked), pool, HEDGE, 400);

        assertNull(race.answer());
        assertEquals(MODELS, race.outrun());
    }

    @Test
    void givesUpWhenEveryModelFails() {
        List<String> asked = new CopyOnWriteArrayList<>();
        GeminiJson.Race race = GeminiJson.firstAnswer(MODELS,
                models(Map.of("lite-a", -1L, "lite-b", -1L, "flash", -1L), asked), pool, HEDGE, BUDGET);

        assertNull(race.winner());
        assertEquals(MODELS, List.copyOf(race.failed().keySet()));
    }

    @Test
    void givesAModelItsPlaceBackOnceTheRestIsOver() {
        Map<String, Long> resting = Map.of("lite-a", NOW - 1);

        assertEquals(MODELS, GeminiJson.tryOrder(MODELS, resting, NOW));
    }
}
