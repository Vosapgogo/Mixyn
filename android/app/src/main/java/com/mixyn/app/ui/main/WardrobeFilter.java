package com.mixyn.app.ui.main;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.FilterSheet;
import com.mixyn.app.ui.components.ItemNames;
import com.mixyn.app.ui.components.OptionsSheet.Option;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What narrows a list on Home down: the choices made in the filter sheet (colors, types, tags, seasons)
 * and the text typed into search. The Clothes tab and the Outfits tab each have one. The lists are
 * already on the phone in full, so filtering happens here and needs no request.
 *
 * A piece of clothing matches when it fits every group that has a choice in it; inside a group one hit
 * is enough (black OR white, a blazer OR a coat).
 *
 * An outfit is asked more of: it must have EVERY chosen color and EVERY chosen kind of piece somewhere
 * among its pieces (black and white: both are in the look). Its category and season work like a
 * piece's tags and season: one of the chosen ones is enough.
 *
 * On the outfit canvas only colors and types are offered, the types sorted by category (Tops: T-shirt,
 * Sweater…; Bottoms: Jeans…), and a type narrows only the carousel it belongs to: choosing jeans
 * changes the bottoms and leaves the tops alone.
 *
 * Search looks at what a piece is called: the name the user gave it, else its color and type ("Black
 * sneakers"), and at its type. An outfit matches when one of its pieces does.
 */
public class WardrobeFilter implements Serializable {

    private static final String COLORS = "colors";
    private static final String TYPES = "types";
    private static final String TAGS = "tags";
    private static final String SEASONS = "seasons";
    /** The categories the canvas filter sorts its types into, in this order, with their titles. */
    private static final String[] CATEGORIES = {Categories.TOP, Categories.BOTTOM, Categories.SHOES,
            Categories.OUTERWEAR, Categories.BAG, Categories.ACCESSORY};
    private static final int[] CATEGORY_TITLES = {R.string.filter_tops, R.string.filter_bottoms,
            R.string.filter_shoes, R.string.filter_outerwear, R.string.filter_bags, R.string.filter_accessories};
    /** A piece or an outfit marked like this is for every season. */
    private static final String ALL_SEASONS = "All";

    /** Color codes ("LIGHT_BLUE"), type codes ("BLAZER"), tag names and season names. */
    private ArrayList<String> colors = new ArrayList<>();
    private ArrayList<String> types = new ArrayList<>();
    private ArrayList<String> tags = new ArrayList<>();
    private ArrayList<String> seasons = new ArrayList<>();
    private String query = "";

    /** How many choices are ticked in the sheet (the search text isn't one of them). */
    public int count() {
        return colors.size() + types.size() + tags.size() + seasons.size();
    }

    /** True when something is narrowing the list: a ticked choice or a typed search. */
    public boolean isActive() {
        return count() > 0 || !query.isEmpty();
    }

    public void setQuery(String typed) {
        query = typed.trim().toLowerCase(Locale.ROOT);
    }

    public void clear() {
        colors.clear();
        types.clear();
        tags.clear();
        seasons.clear();
    }

    /** Takes over what the user ticked in the filter sheet. */
    public void read(Bundle sheetResult) {
        colors = chosen(sheetResult, COLORS);
        // One group of types, or one per category on the canvas ("types_TOP", "types_BOTTOM"…).
        types = new ArrayList<>();
        for (String key : sheetResult.keySet()) {
            if (key.startsWith(TYPES)) types.addAll(chosen(sheetResult, key));
        }
        tags = chosen(sheetResult, TAGS);
        seasons = chosen(sheetResult, SEASONS);
    }

    private static ArrayList<String> chosen(Bundle sheetResult, String key) {
        ArrayList<String> values = sheetResult.getStringArrayList(key);
        return values == null ? new ArrayList<>() : values; // a group the sheet left out
    }

    // ----- Does it match? -----

    public boolean matches(Context context, ItemDto item) {
        return (colors.isEmpty() || overlap(colorCodes(item), colors))
                && (types.isEmpty() || types.contains(item.type))
                && (tags.isEmpty() || overlap(item.tags, tags))
                && inSeason(item.seasons)
                && (query.isEmpty() || isCalled(context, item));
    }

    /**
     * The canvas: the pieces of one carousel (all tops, all bags…) that pass. A piece needs one of the
     * chosen colors; the chosen types count only when this carousel has pieces of one of them.
     */
    public List<ItemDto> narrow(List<ItemDto> carousel) {
        boolean typesHere = false;
        for (ItemDto item : carousel) typesHere |= types.contains(item.type);
        List<ItemDto> passing = new ArrayList<>();
        for (ItemDto item : carousel) {
            if ((colors.isEmpty() || overlap(colorCodes(item), colors))
                    && (!typesHere || types.contains(item.type))) {
                passing.add(item);
            }
        }
        return passing;
    }

    public boolean matches(Context context, OutfitDto outfit) {
        Set<String> colorsWorn = new HashSet<>();
        Set<String> typesWorn = new HashSet<>();
        boolean called = query.isEmpty();
        for (OutfitDto.Piece piece : outfit.pieces) {
            colorsWorn.addAll(colorCodes(piece.item));
            typesWorn.add(piece.item.type);
            called = called || isCalled(context, piece.item);
        }
        return colorsWorn.containsAll(colors)
                && typesWorn.containsAll(types)
                && (tags.isEmpty() || overlap(outfit.tags, tags))
                && inSeason(outfit.seasons)
                && called;
    }

    /** With seasons chosen: marked for one of them, or for all seasons. Not marked at all doesn't count. */
    private boolean inSeason(@Nullable List<String> marked) {
        if (seasons.isEmpty()) return true;
        return marked != null && (marked.contains(ALL_SEASONS) || overlap(marked, seasons));
    }

    private boolean isCalled(Context context, ItemDto item) {
        return ItemNames.of(context, item).toLowerCase(Locale.ROOT).contains(query)
                || (item.typeLabel != null && item.typeLabel.toLowerCase(Locale.ROOT).contains(query));
    }

    private static List<String> colorCodes(ItemDto item) {
        List<String> codes = new ArrayList<>();
        if (item.colors != null) {
            for (ColorDto color : item.colors) codes.add(color.code);
        }
        return codes;
    }

    private static boolean overlap(@Nullable Collection<String> has, Collection<String> wanted) {
        if (has == null) return false;
        for (String value : wanted) {
            if (has.contains(value)) return true;
        }
        return false;
    }

    // ----- The filter sheet for each tab -----

    /** Color, type, tags and season, offering the colors and types these clothes actually have. */
    public ArrayList<FilterSheet.Group> groupsForClothes(Context context, List<ItemDto> clothes) {
        ArrayList<FilterSheet.Group> groups = new ArrayList<>();
        groups.add(new FilterSheet.Group(COLORS, context.getString(R.string.filter_color), null,
                colorOptions(clothes), colors));
        groups.add(new FilterSheet.Group(TYPES, context.getString(R.string.filter_type), null,
                typeOptions(clothes), types));
        groups.add(new FilterSheet.Group(TAGS, context.getString(R.string.filter_tags), null,
                tagOptions(context), tags));
        groups.add(new FilterSheet.Group(SEASONS, context.getString(R.string.filter_season), null,
                seasonOptions(context), seasons));
        return groups;
    }

    /**
     * The canvas offers only color and type, from the clothes in the closet: the types in a "Type"
     * section, one group per category that has any (Tops, Bottoms, Shoes, Outerwear, Bags, Accessories).
     */
    public ArrayList<FilterSheet.Group> groupsForCanvas(Context context, List<ItemDto> clothes) {
        ArrayList<FilterSheet.Group> groups = new ArrayList<>();
        groups.add(new FilterSheet.Group(COLORS, context.getString(R.string.filter_color), null,
                colorOptions(clothes), colors));
        String section = context.getString(R.string.filter_type);
        for (int i = 0; i < CATEGORIES.length; i++) {
            List<ItemDto> inCategory = new ArrayList<>();
            for (ItemDto item : clothes) {
                if (CATEGORIES[i].equals(item.category)) inCategory.add(item);
            }
            ArrayList<Option> options = typeOptions(inCategory);
            ArrayList<String> ticked = new ArrayList<>();
            for (Option option : options) {
                if (types.contains(option.value)) ticked.add(option.value);
            }
            groups.add(new FilterSheet.Group(TYPES + "_" + CATEGORIES[i], context.getString(CATEGORY_TITLES[i]),
                    null, options, ticked, section));
        }
        return groups;
    }

    /** The same four for outfits: the colors and kinds of piece come from the clothes in them. */
    public ArrayList<FilterSheet.Group> groupsForOutfits(Context context, List<OutfitDto> outfits) {
        List<ItemDto> worn = new ArrayList<>();
        for (OutfitDto outfit : outfits) {
            for (OutfitDto.Piece piece : outfit.pieces) worn.add(piece.item);
        }
        ArrayList<FilterSheet.Group> groups = new ArrayList<>();
        groups.add(new FilterSheet.Group(COLORS, context.getString(R.string.filter_outfit_colors),
                context.getString(R.string.filter_outfit_colors_hint), colorOptions(worn), colors));
        groups.add(new FilterSheet.Group(TYPES, context.getString(R.string.filter_outfit_types),
                context.getString(R.string.filter_outfit_types_hint), typeOptions(worn), types));
        groups.add(new FilterSheet.Group(TAGS, context.getString(R.string.filter_outfit_category), null,
                tagOptions(context), tags));
        groups.add(new FilterSheet.Group(SEASONS, context.getString(R.string.filter_season), null,
                seasonOptions(context), seasons));
        return groups;
    }

    /** Every color these clothes have, once, with its dot. */
    private static ArrayList<Option> colorOptions(List<ItemDto> clothes) {
        Map<String, Option> byCode = new LinkedHashMap<>();
        for (ItemDto item : clothes) {
            if (item.colors == null) continue;
            for (ColorDto color : item.colors) {
                byCode.putIfAbsent(color.code, new Option(color.code, color.label, null, Color.parseColor(color.hex)));
            }
        }
        return new ArrayList<>(byCode.values());
    }

    /** Every type these clothes have (Blazer, Jeans…), once, in alphabetical order. */
    private static ArrayList<Option> typeOptions(List<ItemDto> clothes) {
        Map<String, Option> byCode = new LinkedHashMap<>();
        for (ItemDto item : clothes) {
            if (item.type != null) byCode.putIfAbsent(item.type, new Option(item.type, item.typeLabel, null, 0));
        }
        ArrayList<Option> options = new ArrayList<>(byCode.values());
        options.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return options;
    }

    /** The fixed tag list with its dots, as on the item card. */
    private static ArrayList<Option> tagOptions(Context context) {
        String[] names = context.getResources().getStringArray(R.array.tag_names);
        TypedArray dots = context.getResources().obtainTypedArray(R.array.tag_colors);
        ArrayList<Option> options = new ArrayList<>();
        for (int i = 0; i < names.length; i++) options.add(new Option(names[i], names[i], null, dots.getColor(i, 0)));
        dots.recycle();
        return options;
    }

    /** The four seasons. "All" isn't offered: something marked for all seasons matches whichever is chosen. */
    private static ArrayList<Option> seasonOptions(Context context) {
        ArrayList<Option> options = new ArrayList<>();
        for (String name : context.getResources().getStringArray(R.array.season_names)) {
            if (!ALL_SEASONS.equals(name)) options.add(new Option(name, name, null, 0));
        }
        return options;
    }
}
