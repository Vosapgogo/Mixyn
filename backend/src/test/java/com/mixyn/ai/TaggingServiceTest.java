package com.mixyn.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mixyn.ai.TaggingService.Box;
import com.mixyn.ai.TaggingService.Opening;
import com.mixyn.ai.TaggingService.Subject;
import com.mixyn.ai.TaggingService.Tags;
import com.mixyn.item.Category;
import com.mixyn.item.ItemType;

import tools.jackson.databind.ObjectMapper;

/** The model's answer about a photo is untrusted input: only valid values may come out of validate(). */
class TaggingServiceTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void readsAPieceWornByAPerson() throws Exception {
        Tags tags = TaggingService.validate(json.readTree("""
                {"subject": "SINGLE_ITEM", "onBody": true, "box": [20, 150, 480, 900],
                 "type": "HEADSCARF", "category": "ACCESSORY", "colors": ["BEIGE"]}"""));

        assertEquals(Subject.SINGLE_ITEM, tags.subject());
        assertEquals(ItemType.HEADSCARF, tags.type());
        assertEquals(Category.ACCESSORY, tags.category());
        assertTrue(tags.onBody());
        assertEquals(new Box(20, 150, 480, 900), tags.box());
    }

    @Test
    void dropsABoxThatIsNotARectangleInsideThePhoto() throws Exception {
        assertNull(boxOf("[500, 100, 200, 900]")); // upside down
        assertNull(boxOf("[0, 0, 1200, 900]"));    // outside the photo
        assertNull(boxOf("[0, 0, 900]"));          // not four numbers
        assertNull(boxOf("\"everything\""));
    }

    @Test
    void treatsAMissingOnBodyAsNotOnBody() throws Exception {
        Tags tags = TaggingService.validate(json.readTree("""
                {"subject": "SINGLE_ITEM", "type": "ZIP_UP", "category": "OUTERWEAR", "colors": ["GREY"]}"""));

        assertFalse(tags.onBody());
        assertNull(tags.box());
        assertEquals(Category.OUTERWEAR, tags.type().getCategory());
    }

    @Test
    void readsWhereAnOpenCardiganIsOpen() throws Exception {
        Tags tags = TaggingService.validate(json.readTree("""
                {"subject": "SINGLE_ITEM", "onBody": false, "box": [0, 0, 1000, 1000], "front": "OPEN",
                 "opening": [380, 620, 470, 530], "type": "CARDIGAN", "category": "TOP", "colors": ["BROWN"]}"""));

        assertEquals(new Opening(380, 620, 470, 530), tags.opening());
    }

    @Test
    void givesAnOpenPieceATypicalOpeningWhenTheNumbersMakeNoSense() throws Exception {
        // Both neck edges on the same side of the piece.
        // What the model really answered for an open cardigan: a good neck and a nonsense hem.
        assertEquals(new Opening(377, 623, 464, 536), openingOf("OPEN", "[377, 623, 816, 493]"));
        assertEquals(Opening.TYPICAL, openingOf("OPEN", "[100, 300, 150, 250]"));
        assertEquals(Opening.TYPICAL, openingOf("OPEN", "[0, 0, 0, 0]"));
        assertEquals(Opening.TYPICAL, openingOf("OPEN", "\"wide\""));
    }

    @Test
    void ignoresTheOpeningOfAClosedPiece() throws Exception {
        assertNull(openingOf("CLOSED", "[380, 620, 470, 530]"));
    }

    private Opening openingOf(String front, String opening) throws Exception {
        return TaggingService.validate(json.readTree("{\"subject\": \"SINGLE_ITEM\", \"front\": \""
                + front + "\", \"opening\": " + opening + "}")).opening();
    }

    private Box boxOf(String box) throws Exception {
        return TaggingService.validate(json.readTree(
                "{\"subject\": \"SINGLE_ITEM\", \"onBody\": true, \"box\": " + box + "}")).box();
    }
}
