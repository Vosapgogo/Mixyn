package com.mixyn.item;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.mixyn.common.ApiException;

/**
 * The fixed lists of tags and seasons. An item and a saved outfit are labeled from the same lists, and
 * both keep their choice as one comma-separated column ("Casual,Work").
 */
public final class Labels {

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "Casual", "Night out", "Streetwear", "Date night", "Work", "Favorites", "Sports", "Cozy");
    private static final List<String> FOUR_SEASONS = List.of("Spring", "Summer", "Autumn", "Winter");
    private static final Set<String> ALLOWED_SEASONS = Set.of("All", "Spring", "Summer", "Autumn", "Winter");

    private Labels() {
    }

    /** ["Work", "Cozy"] → "Work,Cozy"; an empty list → null. A tag that isn't in the list is refused. */
    public static String joinTags(List<String> tags) {
        if (!allIn(ALLOWED_TAGS, tags)) {
            throw invalid("Choose tags from the list");
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    /** Several seasons are kept in calendar order; "All", or all four together, is stored as just "All". */
    public static String joinSeasons(List<String> chosen) {
        if (!allIn(ALLOWED_SEASONS, chosen)) {
            throw invalid("Choose seasons from the list");
        }
        List<String> seasons = FOUR_SEASONS.stream().filter(chosen::contains).toList();
        if (chosen.contains("All") || seasons.size() == FOUR_SEASONS.size()) {
            return "All";
        }
        return seasons.isEmpty() ? null : String.join(",", seasons);
    }

    /** "Work,Cozy" → ["Work", "Cozy"]; null or empty → []. */
    public static List<String> split(String commaSeparated) {
        return commaSeparated == null || commaSeparated.isBlank()
                ? List.of() : List.of(commaSeparated.split(","));
    }

    /**
     * Whether every value is one of the allowed ones. A null in the list ("tags": [null]) is simply not
     * allowed; asked directly, the fixed set would answer it with an error instead.
     */
    private static boolean allIn(Set<String> allowed, List<String> values) {
        return values.stream().allMatch(value -> value != null && allowed.contains(value));
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "validation_failed", message);
    }
}
