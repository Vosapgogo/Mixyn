package com.mixyn.ai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.mixyn.item.Category;
import com.mixyn.item.ItemColor;
import com.mixyn.item.ItemType;

import tools.jackson.databind.JsonNode;

/** Asks Gemini what a cleaned item photo shows: is it one piece, where is it, its type and colors. */
@Service
public class TaggingService {

    /**
     * What the AI detected. Any field may be null when the AI was unsure or unavailable.
     *
     * @param onBody  true when part of a person is visible in the photo (the piece is worn or held)
     * @param box     where the piece itself is in the photo, or null when the AI gave no usable box
     * @param opening for a piece shown open down the front, where the opening is; null for a closed one
     */
    public record Tags(Subject subject, ItemType type, Category category, List<ItemColor> colors,
            boolean onBody, Box box, Opening opening) {
        public static final Tags NONE = new Tags(null, null, null, List.of(), false, null, null);
    }

    /**
     * The opening of a piece shown open down the front (an open cardigan): the x positions of its two
     * front edges at the neckline and at the hem, in thousandths of the photo's width. A piece worn
     * under it shows between them.
     */
    public record Opening(int neckLeft, int neckRight, int hemLeft, int hemRight) {
        /** Used when the AI says the piece is open but its numbers make no sense: a typical open cardigan. */
        static final Opening TYPICAL = new Opening(370, 630, 460, 540);
    }

    /** A rectangle in the photo, in thousandths of its height (top, bottom) and width (left, right). */
    public record Box(int top, int left, int bottom, int right) {
        public static final int SCALE = 1000;
    }

    /** What the photo shows. Only SINGLE_ITEM belongs in a wardrobe. */
    public enum Subject {
        SINGLE_ITEM, PERSON, MULTIPLE_ITEMS, NOT_CLOTHING
    }

    private static final String PROMPT = """
            This photo should show one clothing item, bag or accessory, with the background removed.
            First decide what it really shows, then classify it:
            - subject:
              SINGLE_ITEM: the photo is clearly about ONE piece. That includes a piece on its own; \
            a pair (shoes, gloves) or only one of the pair; a piece worn or held by a person when it is \
            the obvious subject of the photo (a headscarf on a model's head, a hat, a bag in a hand, shoes \
            on feet), even though a head, neck, hands or legs are visible; and a piece shown with a small \
            detail that belongs to it (a shirt with its tie, a brooch, a dress or coat with its belt).
              PERSON: a person wearing a whole outfit where no single piece is the subject \
            (a full-body look, a selfie).
              MULTIPLE_ITEMS: two or more separate pieces that belong in a wardrobe separately \
            (a shirt with trousers, a top with a jacket over it, several pieces laid out together).
              NOT_CLOTHING: anything else.
            - onBody: true if any part of a person is visible (head, neck, hands, legs), otherwise false.
            - box: the bounding box of the piece itself, not of the person, as [ymin, xmin, ymax, xmax]: \
            integers from 0 to 1000 relative to the image height and width.
            - openFront: true only if the piece is shown open down the front (an open cardigan, an \
            unbuttoned or unzipped shirt or jacket), so that between its two front edges you see the inside \
            of its back, or nothing. False for anything closed or pulled over the head.
            - opening: when openFront is true, where that opening is, as [neckLeft, neckRight, hemLeft, \
            hemRight]: the x positions, from 0 to 1000 across the image width, of the left and the right \
            front edge at the neckline, and then of the same two edges at the bottom hem. \
            When openFront is false, [0, 0, 0, 0].
            - type: the single best match from the allowed list of clothing types. Rules:
              * Outerwear length matters: use COAT, TRENCH_COAT or PUFFER_COAT only for a long piece that \
            reaches well below the hips, and a jacket type (JACKET, PUFFER_JACKET, BLAZER) for a short one.
              * ZIP_UP is a knit, sweatshirt or hoodie that closes with a zip down the whole front. \
            CARDIGAN is an open or buttoned knit. SWEATER, SWEATSHIRT and HOODIE are pulled over the head.
              * Thin jersey tops go by their sleeves: T_SHIRT has short sleeves, LONG_SLEEVE long ones, \
            TANK_TOP none. SHIRT and BLOUSE have a collar or buttons. TOP is for any other top.
              * GLOVES covers gloves and mittens, also when only one of them is in the photo.
              * A shirt shown with a tie is a SHIRT.
            - category: TOP (shirts, t-shirts, sweaters, hoodies, blouses, cardigans, dresses), \
            BOTTOM (trousers, jeans, skirts, shorts), SHOES (any footwear), \
            OUTERWEAR (coats, jackets, blazers, zip-ups worn over a top), \
            BAG (handbags, totes, backpacks, clutches), \
            ACCESSORY (hats, caps, scarves, gloves, sunglasses, belts, jewelry).
            - colors: the item's 1 to 3 most visible colors from the allowed list, the main color first. \
            Use one color for a plain item; add more only for clearly multi-colored items (stripes, prints, \
            color blocks). Ignore tiny details such as buttons, labels and stitching.
            """;

    private static final Schema SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "subject", Schema.builder().type(Type.Known.STRING)
                            .enum_(List.of("SINGLE_ITEM", "PERSON", "MULTIPLE_ITEMS", "NOT_CLOTHING")).build(),
                    "onBody", Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "box", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.INTEGER).build())
                            .minItems(4L).maxItems(4L).build(),
                    "openFront", Schema.builder().type(Type.Known.BOOLEAN).build(),
                    "opening", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.INTEGER).build())
                            .minItems(4L).maxItems(4L).build(),
                    // The allowed values come straight from the ItemType list, so the model can't invent one.
                    "type", Schema.builder().type(Type.Known.STRING)
                            .enum_(Arrays.stream(ItemType.values()).map(Enum::name).toList()).build(),
                    "category", Schema.builder().type(Type.Known.STRING)
                            .enum_(Arrays.stream(Category.values()).map(Enum::name).toList()).build(),
                    "colors", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING)
                                    .enum_(Arrays.stream(ItemColor.values()).map(Enum::name).toList()).build())
                            .minItems(1L).maxItems((long) ItemColor.MAX_PER_ITEM).build()))
            .required(List.of("subject", "onBody", "box", "openFront", "opening", "type", "category", "colors"))
            .build();

    private final GeminiJson gemini;

    public TaggingService(GeminiJson gemini) {
        this.gemini = gemini;
    }

    public Tags tag(byte[] itemPng) {
        byte[] jpeg = onWhiteJpeg(itemPng);
        if (jpeg == null) {
            return Tags.NONE;
        }
        Optional<JsonNode> answer = gemini.generate(PROMPT, jpeg, SCHEMA);
        return answer.map(TaggingService::validate).orElse(Tags.NONE);
    }

    /** The model's answer is untrusted input: keep only values that are really valid. */
    static Tags validate(JsonNode node) {
        Subject subject = null;
        try {
            subject = Subject.valueOf(node.path("subject").asString(""));
        } catch (IllegalArgumentException ignored) {
            // unknown answer → don't block the upload
        }
        Category category = null;
        try {
            category = Category.valueOf(node.path("category").asString(""));
        } catch (IllegalArgumentException ignored) {
            // unknown category → leave it for the user to choose
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode color : node.path("colors")) {
            codes.add(color.asString(""));
        }
        ItemType type = ItemType.fromCode(node.path("type").asString(""));
        // fromCodes drops anything that isn't in the palette and keeps at most three.
        return new Tags(subject, type, category, ItemColor.fromCodes(codes),
                node.path("onBody").asBoolean(false), box(node.path("box")),
                node.path("openFront").asBoolean(false) ? opening(node.path("opening")) : null);
    }

    /**
     * The opening of an open-front piece. The AI's four numbers are kept only if they describe a gap
     * around the middle of the piece that is no wider at the hem than a little over the neck's width;
     * otherwise a typical opening stands in, because the piece is still open.
     */
    private static Opening opening(JsonNode node) {
        if (!node.isArray() || node.size() != 4) {
            return Opening.TYPICAL;
        }
        int[] v = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!node.get(i).isIntegralNumber()) {
                return Opening.TYPICAL;
            }
            v[i] = node.get(i).asInt();
        }
        int middle = Box.SCALE / 2;
        boolean neck = v[0] >= 0 && v[0] < middle && v[1] > middle && v[1] <= Box.SCALE
                && v[1] - v[0] >= 50 && v[1] - v[0] <= 700;
        boolean hem = v[2] >= 0 && v[2] <= v[3] && v[3] <= Box.SCALE && v[3] - v[2] <= 700;
        return neck && hem ? new Opening(v[0], v[1], v[2], v[3]) : Opening.TYPICAL;
    }

    /** [ymin, xmin, ymax, xmax] in thousandths, or null unless it is a real rectangle inside the photo. */
    private static Box box(JsonNode node) {
        if (!node.isArray() || node.size() != 4) {
            return null;
        }
        int[] v = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!node.get(i).isIntegralNumber()) {
                return null;
            }
            v[i] = node.get(i).asInt();
        }
        boolean inside = v[0] >= 0 && v[1] >= 0 && v[2] <= Box.SCALE && v[3] <= Box.SCALE;
        return inside && v[0] < v[2] && v[1] < v[3] ? new Box(v[0], v[1], v[2], v[3]) : null;
    }

    /** Transparent pixels would reach the model as black; put the item on white instead. */
    private static byte[] onWhiteJpeg(byte[] png) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(png));
            BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = flat.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            g.drawImage(source, 0, 0, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(flat, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
