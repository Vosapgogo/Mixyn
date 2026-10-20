package com.mixyn.outfit;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.mixyn.ai.GeminiJson;
import com.mixyn.item.Item;
import com.mixyn.item.ItemRepository;
import com.mixyn.outfit.OutfitDtos.ChatTurn;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.outfit.StylistService.Look;
import com.mixyn.weather.WeatherService;

import tools.jackson.databind.JsonNode;

/**
 * The Stylist chat: the user writes anything about their clothes, and the stylist answers with a short
 * message and, where it helps, up to three outfits or a set of pieces.
 *
 * As with the one-click stylist, the model sees only a text list of the user's items, now with how often
 * and when each was last worn (from the calendar), and answers with ids. Every outfit goes through the
 * same trust boundary (StylistService.buildLook: own items only, each in its place, a complete outfit),
 * and every piece shown must be one of the user's own. A message that isn't about clothes gets nothing
 * but the stylist saying what it helps with. The backend keeps no chat: the phone sends the conversation
 * along with each message.
 */
@Service
public class StylistChatService {

    /** The checked answer. Looks and pieces are empty when the stylist only talks. */
    public record ChatAnswer(String reply, boolean offTopic, List<Look> looks, List<Item> items) {
    }

    /** How often a piece was worn, and when last: the calendar, piece by piece. */
    record Worn(int times, LocalDate last) {
    }

    static final int MAX_LOOKS = 3;
    static final int MAX_ITEMS = 12;
    private static final int MAX_REPLY = 600;
    static final String OFF_TOPIC_REPLY = "I help only with choosing clothes and your style.";
    static final String UNAVAILABLE_REPLY = "The stylist is taking a break. Try again in a moment.";
    static final String EMPTY_CLOSET_REPLY = "Add a few clothes first, and then I can style them for you.";

    private static final String PROMPT = """
            You are Mixyn's personal stylist, chatting with the user about their own wardrobe. Write the \
            reply in the language of the user's message: warm, specific and short (at most 3 sentences, \
            up to 5 when you describe their style or how to improve it).

            You help ONLY with clothes, outfits and the user's style. If the message is about anything \
            else (homework, news, code, recipes, health…), set offTopic to true, leave looks and itemIds \
            empty, and reply only that you help with choosing clothes and their style.

            What you can do, using ONLY the ids in the wardrobe below:
            - Build outfits ("3 outfits for a date", "something for work tomorrow", "what goes well with \
            my grey sweater"): put up to 3 in looks, as many as the user asks for (one if they don't say). \
            Each look has one top, one bottom and one shoes item. A Dress is a top that takes the place of \
            the bottom: with a dress, leave bottomId null unless a bottom really goes under it. A look may \
            add one outerwear item, one under layer (an item listed as TOP/UNDER, worn under another top), \
            one bag and up to 4 accessories of different types. If the user names a piece, every look \
            includes it. Make the looks really different from each other, and dress for the weather.
            - Show pieces ("show all my black pieces", "which shoes do I have", "what haven't I worn for a \
            long time", "what suits me best"): put the matching ids in itemIds, at most 12, the most \
            relevant first. For wearing questions read the worn column: "never" or a date long ago means \
            not worn for a long time; the pieces worn most often are the ones that suit the user best.
            - Describe the user's style from their clothes ("describe my style", "what is my style?"): \
            name it and say what makes it so (the types, colors, tags and what they wear most), and put \
            the 3 to 6 pieces that define it best in itemIds.
            - Suggest how to improve their style ("how can I improve my style?"): 2 or 3 concrete tips \
            with what they already own (new combinations, colors that work together, layers, accessories \
            they rarely wear), and a look or pieces that put a tip into practice. Never suggest buying \
            new clothes: Mixyn is about wearing what you have.
            - Give advice in reply, with looks or itemIds whenever they help.
            If there is an earlier conversation, the new message continues it ("make the second one \
            warmer" means the second outfit you showed last).

            %sToday is %s.

            %sUser's message now: %s

            Wardrobe (id | category | type | colors | name | tags | seasons | worn):
            %s
            """;

    private static final Schema LOOK_SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "topId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "bottomId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "shoesId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "outerwearId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "underId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "bagId", Schema.builder().type(Type.Known.INTEGER).nullable(true).build(),
                    "accessoryIds", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.INTEGER).build()).build()))
            .required(List.of("topId", "bottomId", "shoesId", "outerwearId", "underId", "bagId", "accessoryIds"))
            .build();

    private static final Schema SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "offTopic", Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "reply", Schema.builder().type(Type.Known.STRING).build(),
                    "looks", Schema.builder().type(Type.Known.ARRAY).items(LOOK_SCHEMA).build(),
                    "itemIds", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.INTEGER).build()).build()))
            .required(List.of("offTopic", "reply", "looks", "itemIds"))
            .build();

    private final ItemRepository items;
    private final OutfitRepository outfits;
    private final WearRepository wears;
    private final GeminiJson gemini;
    private final WeatherService weather;
    private final Random random = new Random();

    public StylistChatService(ItemRepository items, OutfitRepository outfits, WearRepository wears,
            GeminiJson gemini, WeatherService weather) {
        this.items = items;
        this.outfits = outfits;
        this.wears = wears;
        this.gemini = gemini;
        this.weather = weather;
    }

    public ChatAnswer answer(Long userId, String message, List<ChatTurn> history) {
        // Only items with a category can stand in an outfit, and only they are worth talking about.
        List<Item> wardrobe = items.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> item.getCategory() != null)
                .toList();
        if (wardrobe.isEmpty()) {
            return new ChatAnswer(EMPTY_CLOSET_REPLY, false, List.of(), List.of());
        }
        Map<Long, Worn> worn = worn(wears.findByUserIdOrderByWornOn(userId),
                outfits.findByUserIdOrderByCreatedAtDesc(userId));
        String prompt = PROMPT.formatted(StylistService.describeWeather(weather.forUser(userId).orElse(null)),
                LocalDate.now(), describeHistory(history), message.trim(), describe(wardrobe, worn));
        Optional<JsonNode> answer = gemini.generate(prompt, null, SCHEMA, true);
        if (answer.isEmpty()) {
            return new ChatAnswer(UNAVAILABLE_REPLY, false, List.of(), List.of());
        }
        return interpret(wardrobe, answer.get(), random);
    }

    /**
     * The trust boundary of the chat. The outfits go through StylistService.buildLook, so each is made
     * of the user's own pieces in their places and is complete; pieces to show must be the user's own,
     * once each. A message the model calls off-topic gets no outfits or pieces at all.
     */
    static ChatAnswer interpret(List<Item> wardrobe, JsonNode node, Random random) {
        String reply = node.path("reply").asString("").trim();
        if (reply.length() > MAX_REPLY) {
            reply = reply.substring(0, MAX_REPLY).trim() + "…";
        }
        if (node.path("offTopic").asBoolean(false)) {
            return new ChatAnswer(reply.isEmpty() ? OFF_TOPIC_REPLY : reply, true, List.of(), List.of());
        }
        List<Look> looks = new ArrayList<>();
        for (JsonNode look : node.path("looks")) {
            if (looks.size() == MAX_LOOKS) {
                break;
            }
            looks.add(StylistService.buildLook(wardrobe, StylistService.parse(look), "", random));
        }
        Map<Long, Item> owned = new HashMap<>();
        for (Item item : wardrobe) {
            owned.put(item.getId(), item);
        }
        Set<Item> shown = new LinkedHashSet<>();
        for (JsonNode id : node.path("itemIds")) {
            Item item = id.isIntegralNumber() ? owned.get(id.asLong()) : null;
            if (item != null && shown.size() < MAX_ITEMS) {
                shown.add(item);
            }
        }
        return new ChatAnswer(reply, false, looks, new ArrayList<>(shown));
    }

    /**
     * For every piece that was ever worn: how many calendar days it was on, and the last one. A day
     * counts for the pieces of the outfit as it was worn that day (an outfit changed later keeps its
     * old version for its old days).
     */
    static Map<Long, Worn> worn(List<Wear> days, List<Outfit> every) {
        Map<Long, Outfit> byId = new HashMap<>();
        for (Outfit outfit : every) {
            byId.put(outfit.getId(), outfit);
        }
        Map<Long, Worn> worn = new HashMap<>();
        for (Wear day : days) {
            Outfit outfit = byId.get(day.getOutfitId());
            if (outfit == null) {
                continue;
            }
            for (Outfit.Piece piece : outfit.getPieces()) {
                Worn before = worn.get(piece.getItemId());
                LocalDate last = before == null || day.getWornOn().isAfter(before.last())
                        ? day.getWornOn() : before.last();
                worn.put(piece.getItemId(), new Worn(before == null ? 1 : before.times() + 1, last));
            }
        }
        return worn;
    }

    /** The one-click stylist's item list with a last column: "never", or "3 times, last 2026-10-12". */
    static String describe(List<Item> wardrobe, Map<Long, Worn> worn) {
        List<String> lines = new ArrayList<>();
        for (Item item : wardrobe) {
            Worn w = worn.get(item.getId());
            String wear = w == null ? "never"
                    : w.times() + (w.times() == 1 ? " time" : " times") + ", last " + w.last();
            lines.add(StylistService.describe(List.of(item)) + " | " + wear);
        }
        return String.join("\n", lines);
    }

    /** The conversation so far, with what the stylist showed each time (ids are only context). */
    static String describeHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder("Earlier in this chat (oldest first):\n");
        for (ChatTurn turn : history) {
            boolean user = "user".equals(turn.role());
            text.append(user ? "User: " : "Stylist: ").append(turn.text() == null ? "" : turn.text().trim());
            if (!user && turn.looks() != null && !turn.looks().isEmpty()) {
                text.append(" [outfits shown:");
                int n = 1;
                for (LookIds look : turn.looks()) {
                    text.append(' ').append(n++).append(") ").append(StylistService.describeIds(look)).append(';');
                }
                text.append(']');
            }
            if (!user && turn.itemIds() != null && !turn.itemIds().isEmpty()) {
                text.append(" [pieces shown: ").append(turn.itemIds()).append(']');
            }
            text.append('\n');
        }
        return text.append('\n').toString();
    }
}
