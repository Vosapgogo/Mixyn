package com.mixyn.outfit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.mixyn.ai.GeminiJson;
import com.mixyn.item.Category;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemRepository;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.outfit.OutfitDtos.PastTurn;
import com.mixyn.weather.WeatherService;
import com.mixyn.weather.WeatherService.Weather;

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
    public record AiPick(Long topId, Long bottomId, Long shoesId, Long outerwearId, Long bagId,
            List<Long> accessoryIds, boolean hintMatched) {
    }

    /**
     * The validated result. A base slot is null only when the wardrobe has nothing for it; the layers
     * (outerwear, bag, accessories) are null / empty unless the stylist validly chose them.
     */
    public record Look(Item top, Item bottom, Item shoes, Item outerwear, Item bag, List<Item> accessories,
            boolean hintMatched, String message, boolean byAi) {
    }

    /** More accessories than this on one outfit is clutter, whatever the model thinks. */
    static final int MAX_ACCESSORIES = 4;

    static final String FALLBACK_MESSAGE = "The stylist is taking a break — here's a random look instead.";

    private static final String PROMPT = """
            You are a personal stylist. Build ONE outfit using ONLY the items listed below; they are the \
            user's own clothes. Pick exactly one TOP, one BOTTOM and one SHOES item by id (if that category \
            has items). On top of that you MAY add optional layers when they suit the look: one OUTERWEAR item, \
            one BAG item, and up to 4 ACCESSORY items of different types (never two head coverings at once). \
            Use null / an empty list for a layer you leave off. An item listed as TOP/OUTERWEAR (a cardigan) \
            can be the top, or the outer layer over another top, but never both in one outfit.

            %s%sUser's request now: %s

            How to use the request:
            - It can be a color ("burgundy"), a mood or style ("cozy", "streetwear"), a specific piece \
            ("my black blazer"), or an occasion / event ("job interview", "first date", "gym", \
            "dinner with parents", "rainy walk"), in any language.
            - For an occasion, first decide what kind of outfit suits it (how formal, how comfortable, \
            what the weather implies), then choose the items that fit best. Read every column: \
            the type (a blazer and loafers are smarter than a hoodie and sneakers), the tags the user \
            gave (Work, Casual, Night out, Date night, Streetwear, Sports, Cozy, Favorites) and the seasons.
            - Always make the colors and styles of the chosen pieces go well together.
            - If the weather is given, dress for it: warmer pieces and an outer layer when it is cold, \
            lighter pieces when it is warm, sensible shoes in rain or snow. In the cold, add a beanie, a scarf \
            and gloves if the user owns them; in hot sunny weather a cap, hat or headscarf and sunglasses. \
            Use the seasons column too. \
            A request that clearly sets another context (indoors, gym, a party) comes before the weather.
            - If there is no request, build a balanced everyday look.
            - If there is an earlier conversation, the new request continues it: keep in mind what the \
            user already asked for. When the new request changes only part of the outfit ("other shoes", \
            "warmer", "without the blazer", "add a bag", "another scarf"), keep the other pieces of the \
            current outfit exactly as they are, layers included, and change only what is needed. When it is a completely new idea, start fresh.
            Set hintMatched to true if the outfit genuinely fits the request (or there is no request); \
            false only if the wardrobe has nothing suitable for it — then still return the best outfit you can.

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
                    "bagId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "accessoryIds", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.INTEGER).build()).build(),
                    "hintMatched", Schema.builder().type(Type.Known.BOOLEAN).build()))
            .required(List.of("topId", "bottomId", "shoesId", "outerwearId", "bagId", "accessoryIds", "hintMatched"))
            .build();

    private static final int MAX_HINT_LENGTH = 500;
    /** Long requests are not repeated inside the "nothing fits" message. */
    private static final int MAX_QUOTED_HINT = 40;

    private final ItemRepository items;
    private final GeminiJson gemini;
    private final WeatherService weather;
    private final Random random = new Random();

    public StylistService(ItemRepository items, GeminiJson gemini, WeatherService weather) {
        this.items = items;
        this.gemini = gemini;
        this.weather = weather;
    }

    public Look generate(Long userId, String hint, List<PastTurn> history, LookIds current) {
        // Only items with a category can stand in an outfit slot.
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> item.getCategory() != null)
                .toList();
        String cleanHint = clean(hint);
        if (wardrobe.isEmpty()) {
            return new Look(null, null, null, null, null, List.of(), true, null, false);
        }
        String prompt = PROMPT.formatted(describeWeather(weather.forUser(userId).orElse(null)),
                describeConversation(history, current),
                cleanHint.isEmpty() ? "(none)" : cleanHint, describe(wardrobe));
        Optional<JsonNode> answer = gemini.generate(prompt, null, SCHEMA);
        AiPick pick = answer.map(StylistService::parse).orElse(null);
        return buildLook(wardrobe, pick, cleanHint, random);
    }

    static String describeWeather(Weather now) {
        return now == null ? ""
                : "Weather where the user is right now: " + now.temperature() + "°C, " + now.sky()
                        + " (feels " + now.feel() + ").\n\n";
    }

    private static String clean(String hint) {
        String text = hint == null ? "" : hint.trim().replaceAll("\\s+", " ");
        return text.length() > MAX_HINT_LENGTH ? text.substring(0, MAX_HINT_LENGTH) : text;
    }

    /**
     * The session's memory, as text for the prompt. The app sends it with every request (the backend
     * keeps no conversation state): earlier requests with the outfit each produced, then the outfit on
     * screen now. Ids here are only context for the model; its answer is validated as always.
     */
    static String describeConversation(List<PastTurn> history, LookIds current) {
        StringBuilder text = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            text.append("Earlier in this conversation (oldest first):\n");
            int n = 1;
            for (PastTurn turn : history) {
                text.append(n++).append(". User asked: \"").append(clean(turn.hint()).isEmpty() ? "(no request)" : clean(turn.hint()))
                        .append("\" -> outfit ").append(describeIds(turn.look())).append('\n');
            }
        }
        if (current != null) {
            text.append("Outfit on screen right now: ").append(describeIds(current)).append('\n');
        }
        return text.isEmpty() ? "" : text.append('\n').toString();
    }

    private static String describeIds(LookIds look) {
        return look == null ? "(unknown)"
                : "top=" + look.topId() + ", bottom=" + look.bottomId() + ", shoes=" + look.shoesId()
                        + ", outerwear=" + look.outerwearId() + ", bag=" + look.bagId()
                        + ", accessories=" + (look.accessoryIds() == null ? List.of() : look.accessoryIds());
    }

    /** One line per item, e.g. "27 | SHOES | Sneakers | Black, White | - | Casual | Summer". */
    static String describe(List<Item> wardrobe) {
        return wardrobe.stream()
                .map(item -> String.join(" | ",
                        String.valueOf(item.getId()),
                        item.getAlsoCategory() == null ? item.getCategory().name()
                                : item.getCategory().name() + "/" + item.getAlsoCategory().name(),
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
        List<Long> accessoryIds = new ArrayList<>();
        for (JsonNode value : node.path("accessoryIds")) {
            if (value.isIntegralNumber()) accessoryIds.add(value.asLong());
        }
        return new AiPick(id(node, "topId"), id(node, "bottomId"), id(node, "shoesId"), id(node, "outerwearId"),
                id(node, "bagId"), accessoryIds, node.path("hintMatched").asBoolean(true));
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
        // A cardigan fits two slots, so it is a candidate for both.
        Map<Category, List<Item>> byCategory = new EnumMap<>(Category.class);
        for (Item item : wardrobe) {
            for (Category slot : Category.values()) {
                if (item.fits(slot)) {
                    byCategory.computeIfAbsent(slot, c -> new ArrayList<>()).add(item);
                }
            }
        }
        boolean aiAnswered = pick != null;

        Item top = choose(byCategory, Category.TOP, aiAnswered ? pick.topId() : null, true, random);
        Item bottom = choose(byCategory, Category.BOTTOM, aiAnswered ? pick.bottomId() : null, true, random);
        Item shoes = choose(byCategory, Category.SHOES, aiAnswered ? pick.shoesId() : null, true, random);
        // The layers are optional: keep one only if the model chose a valid item, never add one at random.
        Item outerwear = choose(byCategory, Category.OUTERWEAR, aiAnswered ? pick.outerwearId() : null, false, random);
        if (outerwear == top) {
            outerwear = null; // the same cardigan can't be the top and the layer over it
        }
        Item bag = choose(byCategory, Category.BAG, aiAnswered ? pick.bagId() : null, false, random);
        List<Item> accessories = chooseAccessories(byCategory.getOrDefault(Category.ACCESSORY, List.of()),
                aiAnswered ? pick.accessoryIds() : null);

        if (!aiAnswered) {
            return new Look(top, bottom, shoes, outerwear, bag, accessories, false, FALLBACK_MESSAGE, false);
        }
        boolean hintGiven = !hint.isEmpty();
        boolean matched = !hintGiven || pick.hintMatched();
        String message = matched ? null
                : "Nothing in your closet quite fits "
                        + (hint.length() <= MAX_QUOTED_HINT ? "\u201C" + hint + "\u201D" : "that request")
                        + ". Here is another stylish option!";
        return new Look(top, bottom, shoes, outerwear, bag, accessories, matched, message, true);
    }

    /** The picked accessories that really are this user's accessories: one per type, at most MAX_ACCESSORIES. */
    private static List<Item> chooseAccessories(List<Item> owned, List<Long> pickedIds) {
        List<Item> chosen = new ArrayList<>();
        if (pickedIds == null) {
            return chosen;
        }
        Set<ItemType> types = new HashSet<>();
        for (Long id : pickedIds) {
            for (Item item : owned) {
                // types.add is false for a second scarf, a second cap…
                if (item.getId().equals(id) && chosen.size() < MAX_ACCESSORIES && types.add(item.getType())) {
                    chosen.add(item);
                }
            }
        }
        return chosen;
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
