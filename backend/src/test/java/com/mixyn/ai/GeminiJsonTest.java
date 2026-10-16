package com.mixyn.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** The order in which the models are tried: one that failed a moment ago waits at the back for a while. */
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

    @Test
    void givesAModelItsPlaceBackOnceTheRestIsOver() {
        Map<String, Long> resting = Map.of("lite-a", NOW - 1);

        assertEquals(MODELS, GeminiJson.tryOrder(MODELS, resting, NOW));
    }
}
