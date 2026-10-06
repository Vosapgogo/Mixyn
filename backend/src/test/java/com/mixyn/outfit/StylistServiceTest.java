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
    private final List<Item> wardrobe = List.of(top, otherTop, bottom, shoes, coat);
    private final Random random = new Random(42);

    @Test
    void keepsAValidPickExactly() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(11L, 20L, 30L, 40L, true), "", random);

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
        Look look = StylistService.buildLook(wardrobe, new AiPick(999L, 20L, 30L, null, true), "", random);

        assertNotNull(look.top());
        assertTrue(wardrobe.contains(look.top()));
        assertEquals(Category.TOP, look.top().getCategory());
    }

    @Test
    void replacesAnItemPutInTheWrongSlot() {
        // The model put the shoes (30) in the top slot and a top (10) in the shoes slot.
        Look look = StylistService.buildLook(wardrobe, new AiPick(30L, 20L, 10L, null, true), "", random);

        assertEquals(Category.TOP, look.top().getCategory());
        assertSame(shoes, look.shoes()); // the only shoes there are
    }

    @Test
    void leavesASlotEmptyWhenTheWardrobeHasNothingForIt() {
        List<Item> noShoes = List.of(top, bottom);
        Look look = StylistService.buildLook(noShoes, new AiPick(10L, 20L, 30L, null, true), "", random);

        assertSame(top, look.top());
        assertSame(bottom, look.bottom());
        assertNull(look.shoes());
    }

    @Test
    void neverAddsOuterwearTheModelDidNotValidlyChoose() {
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, true), "", random).outerwear());
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, 999L, true), "", random).outerwear());
        assertNull(StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, 10L, true), "", random).outerwear());
    }

    @Test
    void givesARandomFullLookWhenTheAiGaveNoAnswer() {
        Look look = StylistService.buildLook(wardrobe, null, "burgundy", random);

        assertEquals(Category.TOP, look.top().getCategory());
        assertEquals(Category.BOTTOM, look.bottom().getCategory());
        assertEquals(Category.SHOES, look.shoes().getCategory());
        assertFalse(look.byAi());
        assertEquals(StylistService.FALLBACK_MESSAGE, look.message());
    }

    @Test
    void explainsWhenNothingMatchesTheHintButStillReturnsALook() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, false), "burgundy", random);

        assertFalse(look.hintMatched());
        assertEquals("We couldn't find anything burgundy. Here is another stylish option!", look.message());
        assertNotNull(look.top());
        assertNotNull(look.bottom());
        assertNotNull(look.shoes());
    }

    @Test
    void ignoresHintMatchedWhenThereWasNoHint() {
        Look look = StylistService.buildLook(wardrobe, new AiPick(10L, 20L, 30L, null, false), "", random);

        assertTrue(look.hintMatched());
        assertNull(look.message());
    }

    /** A wardrobe item with a database id, without needing a database. */
    private static Item item(long id, Category category) {
        Item item = new Item(USER, id + ".png", null, category, List.<ItemColor>of());
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
