package com.mixyn.outfit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.mixyn.item.Category;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.OutfitDtos.LookIds;
import com.mixyn.outfit.OutfitDtos.PastTurn;
import com.mixyn.outfit.StylistService.AiPick;
import com.mixyn.outfit.StylistService.Look;

/**
 * Tests the trust boundary of the AI stylist: whatever the model answers, the look must contain only
 * the user's own items, each in the right slot. No Spring, no database, no network.
 */
class StylistServiceTest {

    private static final long USER = 1L;

    private final Item top = item(10, Category.TOP);
    private final Item otherTop = item(11, Category.TOP);
    private final Item bottom = item(20, Category.BOTTOM);
    private final Item shoes = item(30, Category.SHOES);
    private final Item coat = item(40, Category.OUTERWEAR);
    private final Item bag = item(50, ItemType.TOTE_BAG);
    private final Item scarf = item(60, ItemType.SCARF);
    private final Item otherScarf = item(61, ItemType.SCARF);
    private final Item cap = item(62, ItemType.CAP);
    private final List<Item> wardrobe = List.of(top, otherTop, bottom, shoes, coat, bag, scarf, otherScarf, cap);
    private final Random random = new Random(42);

    @Test
    void keepsAValidPickExactly() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(11L, 20L, 30L, 40L, null, List.of(), true), "", random);

        assertSame(otherTop, look.top());
        assertSame(bottom, look.bottom());
        assertSame(shoes, look.shoes());
        assertSame(coat, look.outerwear());
        assertTrue(look.byAi());
        assertNull(look.message());
    }

    @Test
    void replacesAnIdThatIsNotInThisWardrobe() {
        // 999 could be another user's item or a made-up id: either way it is not in this wardrobe.
        Look look = StylistService.buildLook(wardrobe, new AiPick(999L, 20L, 30L, null, null, List.of(), true), "", random);

        assertNotNull(look.top());
        assertTrue(wardrobe.contains(look.top()));
        assertEquals(Category.TOP, look.top().getCategory());
    }

    @Test
    void replacesAnItemPutInTheWrongSlot() {
        // The model put the shoes (30) in the top slot and a top (10) in the shoes slot.
        Look look = StylistService.buildLook(wardrobe, new AiPick(30L, 20L, 10L, null, null, List.of(), true), "", random);

        assertEquals(Category.TOP, look.top().getCategory());
        assertSame(shoes, look.shoes()); // the only shoes there are
    }

    @Test
    void leavesASlotEmptyWhenTheWardrobeHasNothingForIt() {
        List<Item> noShoes = List.of(top, bottom);
        Look look = StylistService.buildLook(noShoes, new AiPick(10L, 20L, 30L, null, null, List.of(), true), "", random);

        assertSame(top, look.top());
        assertSame(bottom, look.bottom());
        assertNull(look.shoes());
    }

    @Test
    void neverAddsOuterwearTheModelDidNotValidlyChoose() {
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, null, List.of(), true), "", random).outerwear());
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, 999L, null, List.of(), true), "", random).outerwear());
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, 10L, null, List.of(), true), "", random).outerwear());
    }

    @Test
    void keepsAValidBagAndAccessories() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, 50L, List.of(60L, 62L), true),
                "", random);

        assertSame(bag, look.bag());
        assertEquals(List.of(scarf, cap), look.accessories());
    }

    @Test
    void dropsLayersThatAreNotValidBagsOrAccessories() {
        // A top as the bag; as accessories: a made-up id, the bag, a scarf and then a second scarf.
        Look look = StylistService.buildLook(wardrobe,
                new AiPick(10L, 20L, 30L, null, 10L, List.of(999L, 50L, 60L, 61L), true), "", random);

        assertNull(look.bag());
        assertEquals(List.of(scarf), look.accessories());
    }

    @Test
    void letsACardiganBeTheLayerOverAnotherTop() {
        Item cardigan = item(12, ItemType.CARDIGAN);
        List<Item> withCardigan = List.of(top, cardigan, bottom, shoes);

        Look look = StylistService.buildLook(withCardigan, new AiPick(10L, 20L, 30L, 12L, null, List.of(), true),
                "", random);

        assertSame(top, look.top());
        assertSame(cardigan, look.outerwear());
    }

    @Test
    void neverWearsTheSameCardiganAsTopAndLayer() {
        Item cardigan = item(12, ItemType.CARDIGAN);
        List<Item> withCardigan = List.of(top, cardigan, bottom, shoes);

        Look look = StylistService.buildLook(withCardigan, new AiPick(12L, 20L, 30L, 12L, null, List.of(), true),
                "", random);

        assertSame(cardigan, look.top());
        assertNull(look.outerwear());
    }

    @Test
    void tellsTheModelACardiganFitsTwoSlots() {
        String list = StylistService.describe(List.of(item(12, ItemType.CARDIGAN), item(13, ItemType.ZIP_UP)));

        assertTrue(list.contains("12 | TOP/OUTERWEAR | Cardigan"));
        assertTrue(list.contains("13 | OUTERWEAR | Zip-up"));
    }

    @Test
    void givesARandomFullLookWhenTheAiGaveNoAnswer() {
        Look look = StylistService.buildLook(wardrobe, null, "burgundy", random);

        assertEquals(Category.TOP, look.top().getCategory());
        assertEquals(Category.BOTTOM, look.bottom().getCategory());
        assertEquals(Category.SHOES, look.shoes().getCategory());
        assertNull(look.bag());
        assertTrue(look.accessories().isEmpty());
        assertFalse(look.byAi());
        assertEquals(StylistService.FALLBACK_MESSAGE, look.message());
    }

    @Test
    void explainsWhenNothingMatchesTheHintButStillReturnsALook() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, null, List.of(), false), "burgundy", random);

        assertFalse(look.hintMatched());
        assertEquals("Nothing in your closet quite fits \u201Cburgundy\u201D. Here is another stylish option!", look.message());
        assertNotNull(look.top());
        assertNotNull(look.bottom());
        assertNotNull(look.shoes());
    }

    @Test
    void ignoresHintMatchedWhenThereWasNoHint() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, null, List.of(), false), "", random);

        assertTrue(look.hintMatched());
        assertNull(look.message());
    }

    @Test
    void doesNotQuoteAVeryLongRequestInTheMessage() {
        String longHint = "something elegant but comfortable for a long dinner with my parents on a cold evening";
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, null, List.of(), false), longHint, random);

        assertEquals("Nothing in your closet quite fits that request. Here is another stylish option!", look.message());
    }

    @Test
    void putsEarlierRequestsAndTheCurrentOutfitIntoThePrompt() {
        String memory = StylistService.describeConversation(
                List.of(new PastTurn("job interview", new LookIds(10L, 20L, 30L, 40L, 50L, List.of(60L)))),
                new LookIds(11L, 20L, 30L, null, null, null));

        assertTrue(memory.contains("User asked: \"job interview\" -> outfit top=10, bottom=20, shoes=30, outerwear=40, bag=50, accessories=[60]"));
        assertTrue(memory.contains("Outfit on screen right now: top=11, bottom=20, shoes=30, outerwear=null, bag=null, accessories=[]"));
        assertEquals("", StylistService.describeConversation(null, null));
    }

    /** A wardrobe item with a database id, without needing a database. */
    private static Item item(long id, Category category) {
        return item(id, null, category);
    }

    /** The same, with a type; its category follows the type. */
    private static Item item(long id, ItemType type) {
        return item(id, type, type.getCategory());
    }

    private static Item item(long id, ItemType type, Category category) {
        Item item = new Item(USER, id + ".png", type, category, List.<ItemColor>of());
        try {
            Field idField = Item.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return item;
    }
}
