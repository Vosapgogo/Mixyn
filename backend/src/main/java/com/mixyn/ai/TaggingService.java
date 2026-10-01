package com.mixyn.ai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import com.mixyn.item.Category;

import tools.jackson.databind.JsonNode;

/** Asks Gemini what a cleaned item photo shows: its category and main color. */
@Service
public class TaggingService {

    /** What the AI detected. Any field may be null when the AI was unsure or unavailable. */
    public record Tags(Subject subject, Category category, String colorName, String colorHex) {
        public static final Tags NONE = new Tags(null, null, null, null);
    }

    /** What the photo shows. Only SINGLE_ITEM belongs in a wardrobe. */
    public enum Subject {
        SINGLE_ITEM, PERSON, MULTIPLE_ITEMS, NOT_CLOTHING
    }

    private static final String PROMPT = """
            This photo should show exactly one clothing item, with the background removed.
            First decide what it really shows, then classify it:
            - subject: SINGLE_ITEM (one garment or one pair of shoes, not worn by anyone), \
            PERSON (a person or a body part is visible, e.g. someone wearing the clothes, a selfie), \
            MULTIPLE_ITEMS (two or more separate garments, e.g. a whole outfit laid out), \
            NOT_CLOTHING (anything else).
            - category: TOP (shirts, t-shirts, sweaters, hoodies, blouses, dresses), \
            BOTTOM (trousers, jeans, skirts, shorts), SHOES (any footwear), \
            OUTERWEAR (coats, jackets, blazers, cardigans worn over a top).
            - colorName: the main color as one or two plain English words, capitalized (e.g. "Khaki", "Light Blue").
            - colorHex: that color as #RRGGBB.
            """;

    private static final Schema SCHEMA = Schema.builder()
            .type(Type.Known.OBJECT)
            .properties(Map.of(
                    "subject", Schema.builder().type(Type.Known.STRING)
                            .enum_(List.of("SINGLE_ITEM", "PERSON", "MULTIPLE_ITEMS", "NOT_CLOTHING")).build(),
                    "category", Schema.builder().type(Type.Known.STRING)
                            .enum_(List.of("TOP", "BOTTOM", "SHOES", "OUTERWEAR")).build(),
                    "colorName", Schema.builder().type(Type.Known.STRING).build(),
                    "colorHex", Schema.builder().type(Type.Known.STRING).build()))
            .required(List.of("subject", "category", "colorName", "colorHex"))
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
        String name = node.path("colorName").asString("").trim();
        String hex = node.path("colorHex").asString("").trim();
        return new Tags(subject, category,
                name.isEmpty() || name.length() > 30 ? null : capitalize(name),
                hex.matches("#[0-9a-fA-F]{6}") ? hex.toUpperCase() : null);
    }

    /** "light blue" → "Light Blue", so color names look the same everywhere. */
    private static String capitalize(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.toLowerCase().split("\\s+")) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
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
