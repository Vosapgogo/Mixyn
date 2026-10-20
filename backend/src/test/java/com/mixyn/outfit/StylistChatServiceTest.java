package com.mixyn.outfit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.OutfitDtos.ChatTurn;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.outfit.StylistChatService.ChatAnswer;
import com.mixyn.outfit.StylistChatService.Worn;
import com.mixyn.outfit.StylistService.Look;

import tools.jackson.databind.ObjectMapper;

/**
 * Tests the trust boundary of the Stylist chat: whatever the model answers, the outfits are made of the
 * user's own pieces and are complete, the pieces shown are the user's own, and a message that isn't
 * about clothes gets nothing but the answer. Also what the model is told about wearing. No Spring, no
 * database, no network.
 */
class StylistChatServiceTest {

    private static final long USER = 1L;

    private final Item sweater = item(10, ItemType.SWEATER);
    private final Item tee = item(11, ItemType.T_SHIRT);
    private final Item jeans = item(20, ItemType.JEANS);
    private final Item sneakers = item(30, ItemType.SNEAKERS);
    private final Item coat = item(40, ItemType.COAT);
    private final Item dress = item(14, ItemType.DRESS);
    private final List<Item> wardrobe = List.of(sweater, tee, jeans, sneakers, coat, dress);
    private final ObjectMapper json = new ObjectMapper();
    private final Random random = new Random(42);

    @Test
    void everyOutfitIsMadeOfTheUsersOwnPieces() {
        ChatAnswer answer = interpret("""
                {"offTopic": false, "reply": "Two ideas", "itemIds": [], "looks": [
                  {"topId": 10, "bottomId": 20, "shoesId": 30, "outerwearId": 40, "underId": null, "bagId": null, "accessoryIds": []},
                  {"topId": 999, "bottomId": 20, "shoesId": 10, "outerwearId": 30, "underId": null, "bagId": null, "accessoryIds": []}
                ]}""");

        assertEquals(2, answer.looks().size());
        Look first = answer.looks().get(0);
        assertSame(sweater, first.top());
        assertSame(coat, first.outerwear());
        // A made-up top and shoes in the wrong place are replaced; sneakers as outerwear are dropped.
        Look second = answer.looks().get(1);
        assertTrue(wardrobe.contains(second.top()));
        assertSame(sneakers, second.shoes());
        assertNull(second.outerwear());
    }

    @Test
    void showsAtMostThreeOutfits() {
        String look = "{\"topId\": 10, \"bottomId\": 20, \"shoesId\": 30, \"outerwearId\": null, \"underId\": null,"
                + " \"bagId\": null, \"accessoryIds\": []}";
        ChatAnswer answer = interpret("{\"offTopic\": false, \"reply\": \"\", \"itemIds\": [], \"looks\": ["
                + String.join(",", look, look, look, look) + "]}");

        assertEquals(StylistChatService.MAX_LOOKS, answer.looks().size());
    }

    @Test
    void aDressNeedsNoBottom() {
        ChatAnswer answer = interpret("""
                {"offTopic": false, "reply": "", "itemIds": [], "looks": [
                  {"topId": 14, "bottomId": null, "shoesId": 30, "outerwearId": null, "underId": null, "bagId": null, "accessoryIds": []}
                ]}""");

        assertSame(dress, answer.looks().get(0).top());
        assertNull(answer.looks().get(0).bottom());
    }

    @Test
    void showsOnlyTheUsersOwnPiecesOnceEach() {
        ChatAnswer answer = interpret("""
                {"offTopic": false, "reply": "Your black pieces", "looks": [], "itemIds": [30, 999, 30, 20]}""");

        assertEquals(List.of(sneakers, jeans), answer.items());
        assertTrue(answer.looks().isEmpty());
    }

    @Test
    void aMessageThatIsNotAboutClothesGetsOnlyTheAnswer() {
        ChatAnswer answer = interpret("""
                {"offTopic": true, "reply": "", "itemIds": [10], "looks": [
                  {"topId": 10, "bottomId": 20, "shoesId": 30, "outerwearId": null, "underId": null, "bagId": null, "accessoryIds": []}
                ]}""");

        assertTrue(answer.offTopic());
        assertEquals(StylistChatService.OFF_TOPIC_REPLY, answer.reply());
        assertTrue(answer.looks().isEmpty());
        assertTrue(answer.items().isEmpty());
    }

    @Test
    void countsHowOftenAndWhenEachPieceWasWorn() {
        Outfit casual = outfit(1, new Outfit.Piece(Slot.TOP, 10L), new Outfit.Piece(Slot.BOTTOM, 20L));
        Outfit smart = outfit(2, new Outfit.Piece(Slot.TOP, 10L), new Outfit.Piece(Slot.OUTERWEAR, 40L));
        List<Wear> days = List.of(
                new Wear(USER, 1L, LocalDate.of(2026, 10, 1)),
                new Wear(USER, 2L, LocalDate.of(2026, 10, 12)),
                new Wear(USER, 1L, LocalDate.of(2026, 10, 5)));

        Map<Long, Worn> worn = StylistChatService.worn(days, List.of(casual, smart));

        assertEquals(new Worn(3, LocalDate.of(2026, 10, 12)), worn.get(10L));
        assertEquals(new Worn(2, LocalDate.of(2026, 10, 5)), worn.get(20L));
        assertNull(worn.get(30L));
        String list = StylistChatService.describe(List.of(sweater, sneakers), worn);
        assertTrue(list.contains("| 3 times, last 2026-10-12"), list);
        assertTrue(list.contains("30 | SHOES | Sneakers") && list.endsWith("| never"), list);
    }

    @Test
    void tellsTheModelWhatItShowedBefore() {
        String history = StylistChatService.describeHistory(List.of(
                new ChatTurn("user", "2 looks for work", null, null),
                new ChatTurn("stylist", "Here you go", List.of(new LookIds(10L, 20L, 30L, null, null, null, List.of())),
                        List.of(40L))));

        assertTrue(history.contains("User: 2 looks for work"), history);
        assertTrue(history.contains("1) top=10, under=null, bottom=20, shoes=30"), history);
        assertTrue(history.contains("[pieces shown: [40]]"), history);
        assertFalse(StylistChatService.describeHistory(List.of()).contains("Earlier"));
    }

    private ChatAnswer interpret(String answer) {
        return StylistChatService.interpret(wardrobe, json.readTree(answer), random);
    }

    /** An outfit as it would come from the database: the id is set by reflection, as JPA does. */
    private static Outfit outfit(long id, Outfit.Piece... pieces) {
        Outfit outfit = new Outfit(USER, id + ".png", List.of(pieces));
        setId(Outfit.class, outfit, id);
        return outfit;
    }

    private static Item item(long id, ItemType type) {
        Item item = new Item(USER, id + ".png", type, type.getCategory(), List.<ItemColor>of());
        setId(Item.class, item, id);
        return item;
    }

    private static void setId(Class<?> type, Object entity, long id) {
        try {
            Field idField = type.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
