package com.mixyn.outfit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mixyn.common.ApiException;
import com.mixyn.item.Item;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemType;
import com.mixyn.outfit.Outfit.Piece;
import com.mixyn.outfit.OutfitDtos.LookIds;

/**
 * Tests the trust boundary of saving an outfit: whatever ids the app sends, an outfit is made only of
 * the user's own items, each in a place it can be worn. No Spring, no database, no network.
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
    void savesAnOutfitWithEmptyPlaces() {
        List<Piece> pieces = OutfitService.checkLook(wardrobe, new LookIds(10L, 20L, null, null, null, null, null));

        assertEquals(List.of(10L, 20L), pieces.stream().map(Piece::getItemId).toList());
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

    private void assertRefused(LookIds look) {
        ApiException refused = assertThrows(ApiException.class, () -> OutfitService.checkLook(wardrobe, look));
        assertEquals("validation_failed", refused.getCode());
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
