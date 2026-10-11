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

/** Asks Gemini what a cleaned item photo shows: its category and main color. */
@Service
public class TaggingService {

    /** What the AI detected. Any field may be null when the AI was unsure or unavailable. */
    public record Tags(Subject subject, ItemType type, Category category, List<ItemColor> colors) {
        public static final Tags NONE = new Tags(null, null, null, List.of());
    }

    /** What the photo shows. Only SINGLE_ITEM belongs in a wardrobe. */
    public enum Subject {
        SINGLE_ITEM, PERSON, MULTIPLE_ITEMS, NOT_CLOTHING
    }

    private static final String PROMPT = """
            This photo should show exactly one clothing item, bag or accessory, with the background removed.
            First decide what it really shows, then classify it:
            - subject: SINGLE_ITEM (one garment, one pair of shoes or gloves, one bag or one accessory, not worn by anyone), \
            PERSON (a person or a body part is visible, e.g. someone wearing the clothes, a selfie), \
            MULTIPLE_ITEMS (two or more separate garments, e.g. a whole outfit laid out), \
            NOT_CLOTHING (anything else).
            - type: the single best match from the allowed list of clothing types. For outerwear, \
            length matters: use COAT, TRENCH_COAT or PUFFER_COAT only for a long piece that reaches \
            well below the hips, and a jacket type (JACKET, PUFFER_JACKET, BLAZER…) for a short one.
            - category: TOP (shirts, t-shirts, sweaters, hoodies, blouses, dresses), \
            BOTTOM (trousers, jeans, skirts, shorts), SHOES (any footwear), \
            OUTERWEAR (coats, jackets, blazers, cardigans worn over a top), \
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
                    // The allowed values come straight from the ItemType list, so the model can't invent one.
                    "type", Schema.builder().type(Type.Known.STRING)
                            .enum_(Arrays.stream(ItemType.values()).map(Enum::name).toList()).build(),
                    "category", Schema.builder().type(Type.Known.STRING)
                            .enum_(Arrays.stream(Category.values()).map(Enum::name).toList()).build(),
                    "colors", Schema.builder().type(Type.Known.ARRAY)
                            .items(Schema.builder().type(Type.Known.STRING)
                                    .enum_(Arrays.stream(ItemColor.values()).map(Enum::name).toList()).build())
                            .minItems(1L).maxItems((long) ItemColor.MAX_PER_ITEM).build()))
            .required(List.of("subject", "type", "category", "colors"))
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
        return new Tags(subject, type, category, ItemColor.fromCodes(codes));
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
