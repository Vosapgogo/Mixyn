package com.mixyn.outfit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.Outfit.Piece;
import com.mixyn.outfit.OutfitDtos.LookIds;

/**
 * Tests the trust boundary of saving an outfit: whatever ids the app sends, an outfit is made only of
 * the user's own items, each in a place it can be worn. Also what may not happen to saved outfits: the
 * same outfit twice, and a piece whose new type leaves it in a place it can't be worn. No Spring, no
 * database, no network.
 */
class OutfitServiceTest {

    private static final long USER = 1L;

    private final Item sweater = item(10, ItemType.SWEATER);
    private final Item tee = item(11, ItemType.T_SHIRT);
    private final Item cardigan = item(12, ItemType.CARDIGAN);
    private final Item jeans = item(20, ItemType.JEANS);
    private final Item sneakers = item(30, ItemType.SNEAKERS);
    private final Item coat = item(40, ItemType.COAT);
    private final Item tote = item(50, ItemType.TOTE_BAG);
    private final Item scarf = item(60, ItemType.SCARF);
    private final Item cap = item(61, ItemType.CAP);
    private final List<Item> wardrobe = List.of(sweater, tee, cardigan, jeans, sneakers, coat, tote, scarf, cap);

    @Test
    void keepsEveryPieceInItsPlace() {
        List<Piece> pieces = OutfitService.checkLook(wardrobe,
                new LookIds(10L, 20L, 30L, 40L, 11L, 50L, List.of(60L, 61L)));

        assertEquals(List.of(Slot.TOP, Slot.BOTTOM, Slot.SHOES, Slot.OUTERWEAR, Slot.UNDER, Slot.BAG,
                Slot.ACCESSORY, Slot.ACCESSORY), pieces.stream().map(Piece::getSlot).toList());
        assertEquals(List.of(10L, 20L, 30L, 40L, 11L, 50L, 60L, 61L),
                pieces.stream().map(Piece::getItemId).toList());
    }

    @Test
    void theLayersMayBeLeftOut() {
        List<Piece> pieces = OutfitService.checkLook(wardrobe, new LookIds(10L, 20L, 30L, null, null, null, null));

        assertEquals(List.of(10L, 20L, 30L), pieces.stream().map(Piece::getItemId).toList());
    }

    @Test
    void refusesAnOutfitWithoutATopBottomsAndShoes() {
        assertRefusedFor(new LookIds(10L, 20L, null, 40L, null, 50L, null), "Add shoes to complete the look.");
        assertRefusedFor(new LookIds(null, 20L, 30L, 40L, null, null, null), "Add a top to complete the look.");
        assertRefusedFor(new LookIds(10L, null, 30L, null, null, null, List.of(60L)),
                "Add bottoms to complete the look.");
        assertRefusedFor(new LookIds(10L, null, null, null, null, null, null),
                "Add bottoms and shoes to complete the look.");
        // A coat over nothing is not an outfit either.
        assertRefusedFor(new LookIds(null, null, null, 40L, null, null, null),
                "Add a top, bottoms and shoes to complete the look.");
    }

    @Test
    void acceptsACardiganAsOuterwear() {
        List<Piece> pieces = OutfitService.checkLook(wardrobe, new LookIds(10L, 20L, 30L, 12L, null, null, null));

        assertEquals(Slot.OUTERWEAR, pieces.get(3).getSlot());
    }

    @Test
    void refusesAnIdThatIsNotInThisWardrobe() {
        // 999 could be another user's item or a made-up id: either way it is not in this wardrobe.
        assertRefused(new LookIds(999L, 20L, 30L, null, null, null, null));
        assertRefused(new LookIds(10L, 20L, 30L, null, null, null, List.of(999L)));
    }

    @Test
    void refusesAPieceInAPlaceItCannotBeWorn() {
        assertRefused(new LookIds(20L, 10L, 30L, null, null, null, null)); // jeans as the top
        assertRefused(new LookIds(10L, 20L, 30L, 60L, null, null, null)); // a scarf as outerwear
        assertRefused(new LookIds(11L, 20L, 30L, null, 10L, null, null)); // a sweater under a T-shirt
        assertRefused(new LookIds(10L, 20L, 30L, null, null, null, List.of(50L))); // a bag as an accessory
    }

    @Test
    void refusesTheSamePieceTwice() {
        assertRefused(new LookIds(12L, 20L, 30L, 12L, null, null, null)); // a cardigan over itself
        assertRefused(new LookIds(11L, 20L, 30L, null, 11L, null, null)); // a T-shirt under itself
    }

    @Test
    void refusesAnEmptyOutfit() {
        assertRefused(new LookIds(null, null, null, null, null, null, List.of()));
    }

    // ----- The same outfit twice -----

    @Test
    void findsAnOutfitWithTheSamePiecesInTheSamePlaces() {
        Outfit saved = outfit(1, new Piece(Slot.TOP, 10L), new Piece(Slot.BOTTOM, 20L));
        // the same pieces, listed in another order
        List<Piece> again = List.of(new Piece(Slot.BOTTOM, 20L), new Piece(Slot.TOP, 10L));

        assertSame(saved, OutfitService.duplicateOf(List.of(saved), again, null));
    }

    @Test
    void anotherPieceOrAnotherPlaceIsNotTheSameOutfit() {
        Outfit saved = outfit(1, new Piece(Slot.TOP, 12L), new Piece(Slot.BOTTOM, 20L));

        assertNull(OutfitService.duplicateOf(List.of(saved),
                List.of(new Piece(Slot.TOP, 12L), new Piece(Slot.BOTTOM, 20L), new Piece(Slot.SHOES, 30L)), null));
        // the cardigan as outerwear over the sweater is not the cardigan as the top
        assertNull(OutfitService.duplicateOf(List.of(saved),
                List.of(new Piece(Slot.OUTERWEAR, 12L), new Piece(Slot.BOTTOM, 20L)), null));
    }

    @Test
    void anUpdatedOutfitIsNotACopyOfItself() {
        Outfit saved = outfit(1, new Piece(Slot.TOP, 10L));

        assertNull(OutfitService.duplicateOf(List.of(saved), List.of(new Piece(Slot.TOP, 10L)), 1L));
    }

    // ----- A piece's type changes -----

    @Test
    void aNewTypeThatStillFitsItsPlaceChangesNothing() {
        Outfit look = outfit(1, new Piece(Slot.TOP, 11L), new Piece(Slot.BOTTOM, 20L));

        OutfitService.TypeChange change = OutfitService.planTypeChange(tee, ItemType.LONG_SLEEVE, List.of(look), byId());

        assertTrue(change.moves().isEmpty());
        assertTrue(change.conflicts().isEmpty());
    }

    @Test
    void aPieceMovesToItsNewPlaceWhenThatPlaceIsFree() {
        // a T-shirt that is really a skirt, in an outfit without a bottom
        Outfit look = outfit(1, new Piece(Slot.TOP, 11L), new Piece(Slot.SHOES, 30L));

        OutfitService.TypeChange change = OutfitService.planTypeChange(tee, ItemType.SKIRT, List.of(look), byId());

        assertTrue(change.conflicts().isEmpty());
        assertEquals(1, change.moves().size());
        assertEquals(Slot.BOTTOM, change.moves().get(0).to());
        assertEquals(0, change.moves().get(0).position());
    }

    @Test
    void aPieceCantMoveWhereTheOutfitAlreadyHasAPiece() {
        Outfit look = outfit(1, new Piece(Slot.TOP, 11L), new Piece(Slot.BOTTOM, 20L));

        OutfitService.TypeChange change = OutfitService.planTypeChange(tee, ItemType.SKIRT, List.of(look), byId());

        assertTrue(change.moves().isEmpty());
        assertEquals(1, change.conflicts().size());
        assertEquals(Slot.TOP, change.conflicts().get(0).from());
        assertEquals(Slot.BOTTOM, change.conflicts().get(0).to());
        assertSame(jeans, change.conflicts().get(0).taking());
    }

    @Test
    void aPieceUnderTheTopThatIsNoLongerThinHasNowhereToGo() {
        // the T-shirt under the sweater turns out to be a hoodie: it can't be under, and the top is taken
        Outfit look = outfit(1, new Piece(Slot.TOP, 10L), new Piece(Slot.UNDER, 11L));

        OutfitService.TypeChange change = OutfitService.planTypeChange(tee, ItemType.HOODIE, List.of(look), byId());

        assertEquals(1, change.conflicts().size());
        assertSame(sweater, change.conflicts().get(0).taking());
    }

    @Test
    void anotherAccessoryOfAnotherTypeLeavesRoom() {
        // a cap that is really a scarf: the outfit's other accessory is a cap, so the scarf fits beside it
        Outfit look = outfit(1, new Piece(Slot.TOP, 10L), new Piece(Slot.ACCESSORY, 61L), new Piece(Slot.BAG, 50L));
        Item otherCap = item(62, ItemType.CAP);
        Outfit withTwo = outfit(2, new Piece(Slot.ACCESSORY, 61L), new Piece(Slot.ACCESSORY, 62L));
        Map<Long, Item> owned = byId();
        owned.put(62L, otherCap);

        // the cap becomes a scarf: still an accessory, nothing moves in either outfit
        OutfitService.TypeChange change = OutfitService.planTypeChange(cap, ItemType.SCARF, List.of(look, withTwo), owned);

        assertTrue(change.moves().isEmpty());
        assertTrue(change.conflicts().isEmpty());
    }

    @Test
    void aBagThatIsReallyAnAccessoryMovesNextToTheOthers() {
        Outfit look = outfit(1, new Piece(Slot.BAG, 50L), new Piece(Slot.ACCESSORY, 60L));

        OutfitService.TypeChange change = OutfitService.planTypeChange(tote, ItemType.BELT, List.of(look), byId());

        assertTrue(change.conflicts().isEmpty());
        assertEquals(Slot.ACCESSORY, change.moves().get(0).to());
    }

    private Map<Long, Item> byId() {
        Map<Long, Item> owned = new HashMap<>();
        for (Item item : wardrobe) owned.put(item.getId(), item);
        return owned;
    }

    /** A saved outfit with these pieces, its id set by reflection, as JPA does. */
    private static Outfit outfit(long id, Piece... pieces) {
        Outfit outfit = new Outfit(USER, id + ".png", List.of(pieces));
        try {
            Field idField = Outfit.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(outfit, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return outfit;
    }

    private void assertRefused(LookIds look) {
        ApiException refused = assertThrows(ApiException.class, () -> OutfitService.checkLook(wardrobe, look));
        assertEquals("validation_failed", refused.getCode());
    }

    /** Refused, and the message says which of the top, bottoms and shoes the outfit still needs. */
    private void assertRefusedFor(LookIds look, String advice) {
        ApiException refused = assertThrows(ApiException.class, () -> OutfitService.checkLook(wardrobe, look));
        assertEquals("validation_failed", refused.getCode());
        assertTrue(refused.getMessage().endsWith(advice), refused.getMessage());
    }

    /** An item as it would come from the database: the id is set by reflection, as JPA does. */
    private static Item item(long id, ItemType type) {
        Item item = new Item(USER, id + ".png", type, type.getCategory(), List.<ItemColor>of());
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
