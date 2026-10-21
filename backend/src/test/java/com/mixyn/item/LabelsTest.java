package com.mixyn.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mixyn.common.ApiException;

/** Tests the fixed lists of tags and seasons: what is kept, and that anything else is a clear 400. */
class LabelsTest {

    @Test
    void keepsTagsAndSeasonsFromTheLists() {
        assertEquals("Work,Cozy", Labels.joinTags(List.of("Work", "Cozy")));
        assertNull(Labels.joinTags(List.of()));
        assertEquals("Spring,Autumn", Labels.joinSeasons(List.of("Autumn", "Spring")));
        assertEquals("All", Labels.joinSeasons(List.of("Spring", "Summer", "Autumn", "Winter")));
    }

    @Test
    void refusesAnUnknownOrMissingValue() {
        for (List<String> tags : List.of(List.of("Party"), Arrays.asList("Work", null))) {
            ApiException refused = assertThrows(ApiException.class, () -> Labels.joinTags(tags));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        }
        for (List<String> seasons : List.of(List.of("Monsoon"), Arrays.asList((String) null))) {
            ApiException refused = assertThrows(ApiException.class, () -> Labels.joinSeasons(seasons));
            assertEquals(HttpStatus.BAD_REQUEST, refused.getStatus());
        }
    }
}
