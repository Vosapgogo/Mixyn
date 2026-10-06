package com.mixyn.outfit;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.mixyn.ai.GeminiJson;
import com.mixyn.item.Category;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemRepository;

import tools.jackson.databind.JsonNode;

/**
 * The AI stylist: builds a look from the user's own wardrobe.
 *
 * The model only ever sees a text list of the user's items and answers with item ids. That answer is
 * treated as untrusted input: every id is checked (is it this user's item? is it in the right slot?)
 * and anything wrong or missing is replaced with a random valid item. So whatever the model says —
 * or if it says nothing at all — the result is always a look made only of clothes the user owns.
 */
@Service
public class StylistService {

    /** What the model picked. Ids may be null, unknown, or point at the wrong kind of item. */
    public record AiPick(Long topId, Long bottomId, Long shoesId, Long outerwearId, boolean hintMatched) {
    }

    /** The validated result. A slot is null only when the wardrobe has nothing for it (or no outerwear was added). */
    public record Look(Item top, Item bottom, Item shoes, Item outerwear, boolean hintMatched, String message,
            boolean byAi) {
    }

    static final String FALLBACK_MESSAGE = "The stylist is taking a break — here's a random look instead.";

    private static final String PROMPT = """
            You are a personal stylist. Build ONE outfit using ONLY the items listed below; they are the \
            user's own clothes. Pick exactly one TOP, one BOTTOM and one SHOES item by id (if that category \
            has items). You MAY add one OUTERWEAR item if it suits the look; otherwise use null.
            Choose pieces whose colors and style go well together.

            User's request: %s
            If there is a request, favor items that match it (a color, a mood, a type of garment, or a \
            specific piece) and build the rest of the look around them. Set hintMatched to true if at \
            least one chosen item really matches the request, or if there is no request; false if nothing \
            in the wardrobe matches it (then still return a good outfit).

            Wardrobe (id | category | type | colors | name | tags | seasons):
            %s
            """;

    private static final Schema SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "topId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "bottomId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "shoesId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "outerwearId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "hintMatched", Schema.builder().type(Type.Known.BOOLEAN).build()))
            .required(List.of("topId", "bottomId", "shoesId", "outerwearId", "hintMatched"))
            .build();

    private static final int MAX_HINT_LENGTH = 100;

    private final ItemRepository items;
    private final GeminiJson gemini;
    private final Random random = new Random();

    public StylistService(ItemRepository items, GeminiJson gemini) {
        this.items = items;
        this.gemini = gemini;
    }

    public Look generate(Long userId, String hint) {
        // Only items with a category can stand in an outfit slot.
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> item.getCategory() != null)
                .toList();
        String cleanHint = hint == null ? "" : hint.trim();
        if (cleanHint.length() > MAX_HINT_LENGTH) {
            cleanHint = cleanHint.substring(0, MAX_HINT_LENGTH);
        }
        if (wardrobe.isEmpty()) {
            return new Look(null, null, null, null, true, null, false);
        }
        String prompt = PROMPT.formatted(cleanHint.isEmpty() ? "(none)" : cleanHint, describe(wardrobe));
        Optional<JsonNode> answer = gemini.generate(prompt, null, SCHEMA);
        AiPick pick = answer.map(StylistService::parse).orElse(null);
        return buildLook(wardrobe, pick, cleanHint, random);
    }

    /** One line per item, e.g. "27 | SHOES | Sneakers | Black, White | - | Casual | Summer". */
    static String describe(List<Item> wardrobe) {
        return wardrobe.stream()
                .map(item -> String.join(" | ",
                        String.valueOf(item.getId()),
                        item.getCategory().name(),
                        item.getType() == null ? "-" : item.getType().getLabel(),
                        item.getColors().isEmpty() ? "-"
                                : item.getColors().stream().map(ItemColor::getLabel).collect(Collectors.joining(", ")),
                        orDash(item.getName()),
                        orDash(item.getTags()),
                        orDash(item.getSeason())))
                .collect(Collectors.joining("\n"));
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static AiPick parse(JsonNode node) {
        return new AiPick(id(node, "topId"), id(node, "bottomId"), id(node, "shoesId"), id(node, "outerwearId"),
                node.path("hintMatched").asBoolean(true));
    }

    private static Long id(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asLong() : null;
    }

    /**
     * The trust boundary. Turns the model's pick (or null, if the AI gave no usable answer) into a look
     * that contains only items from this wardrobe, each in its own slot.
     */
    static Look buildLook(List<Item> wardrobe, AiPick pick, String hint, Random random) {
        Map<Category, List<Item>> byCategory = new EnumMap<>(Category.class);
        for (Item item : wardrobe) {
            byCategory.computeIfAbsent(item.getCategory(), c -> new java.util.ArrayList<>()).add(item);
        }
        boolean aiAnswered = pick != null;

        Item top = choose(byCategory, Category.TOP, aiAnswered ? pick.topId() : null, true, random);
        Item bottom = choose(byCategory, Category.BOTTOM, aiAnswered ? pick.bottomId() : null, true, random);
        Item shoes = choose(byCategory, Category.SHOES, aiAnswered ? pick.shoesId() : null, true, random);
        // Outerwear is optional: keep it only if the model chose a valid one, never add one at random.
        Item outerwear = choose(byCategory, Category.OUTERWEAR, aiAnswered ? pick.outerwearId() : null, false, random);

        if (!aiAnswered) {
            return new Look(top, bottom, shoes, outerwear, false, FALLBACK_MESSAGE, false);
        }
        boolean hintGiven = !hint.isEmpty();
        boolean matched = !hintGiven || pick.hintMatched();
        String message = matched ? null
                : "We couldn't find anything " + hint + ". Here is another stylish option!";
        return new Look(top, bottom, shoes, outerwear, matched, message, true);
    }

    /**
     * The item with the picked id, but only if it exists in this wardrobe AND belongs to this slot.
     * Otherwise a random item of the slot (when fillIfInvalid), or null if the slot has no items.
     */
    private static Item choose(Map<Category, List<Item>> byCategory, Category slot, Long pickedId,
            boolean fillIfInvalid, Random random) {
        List<Item> candidates = byCategory.getOrDefault(slot, List.of());
        if (pickedId != null) {
            for (Item item : candidates) {
                if (pickedId.equals(item.getId())) {
                    return item;
                }
            }
        }
        if (!fillIfInvalid || candidates.isEmpty()) {
            return null;
        }
        return candidates.get(random.nextInt(candidates.size()));
    }
}
