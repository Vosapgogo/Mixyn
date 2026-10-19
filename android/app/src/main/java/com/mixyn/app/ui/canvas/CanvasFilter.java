package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.os.Bundle;

import com.mixyn.app.R;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.FilterSheet;
import com.mixyn.app.ui.components.OptionsSheet.Option;
import com.mixyn.app.ui.main.WardrobeFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The filter on the outfit canvas: for each category (tops, bottoms, shoes, outerwear, bags,
 * accessories) the types and the colors to show in its carousel. Every category is filtered on its own,
 * so red sweaters and blue trousers can be asked for together, and what is ticked stays ticked when the
 * filter is opened again, so new choices are added to the ones made before.
 *
 * Inside one category a piece needs one of the chosen types (if any are chosen) and one of the chosen
 * colors (if any are chosen).
 */
public class CanvasFilter {

    /** The categories in the order the sheet shows them, with their titles. */
    private static final String[] CATEGORIES = {Categories.TOP, Categories.BOTTOM, Categories.SHOES,
            Categories.OUTERWEAR, Categories.BAG, Categories.ACCESSORY};
    private static final int[] TITLES = {R.string.filter_tops, R.string.filter_bottoms, R.string.filter_shoes,
            R.string.filter_outerwear, R.string.filter_bags, R.string.filter_accessories};
    private static final String TYPES = "/types";
    private static final String COLORS = "/colors";

    /** What is ticked, by group key: "TOP/types" → ["SWEATER"], "BOTTOM/colors" → ["BLUE"]… */
    private final Map<String, ArrayList<String>> chosen = new HashMap<>();

    /** How many choices are ticked in all. */
    public int count() {
        int count = 0;
        for (List<String> values : chosen.values()) count += values.size();
        return count;
    }

    public void clear() {
        chosen.clear();
    }

    /** Takes over what the user ticked in the filter sheet. */
    public void read(Bundle sheetResult) {
        chosen.clear();
        for (String key : sheetResult.keySet()) {
            ArrayList<String> values = sheetResult.getStringArrayList(key);
            if (values != null) chosen.put(key, values);
        }
    }

    /** The pieces of one carousel that pass the choices made for this category. */
    public List<ItemDto> narrow(String category, List<ItemDto> pieces) {
        List<String> types = chosen(category + TYPES);
        List<String> colors = chosen(category + COLORS);
        List<ItemDto> passing = new ArrayList<>();
        for (ItemDto item : pieces) {
            if ((types.isEmpty() || types.contains(item.type)) && (colors.isEmpty() || hasColor(item, colors))) {
                passing.add(item);
            }
        }
        return passing;
    }

    /**
     * For every category the closet has, a section with its Type and Color groups, offering the types
     * and colors its pieces actually have.
     */
    public ArrayList<FilterSheet.Group> groups(Context context, List<ItemDto> clothes) {
        ArrayList<FilterSheet.Group> groups = new ArrayList<>();
        for (int i = 0; i < CATEGORIES.length; i++) {
            List<ItemDto> inCategory = new ArrayList<>();
            for (ItemDto item : clothes) {
                if (CATEGORIES[i].equals(item.category)) inCategory.add(item);
            }
            String section = context.getString(TITLES[i]);
            groups.add(group(CATEGORIES[i] + TYPES, context.getString(R.string.filter_type),
                    WardrobeFilter.typeOptions(inCategory), section));
            groups.add(group(CATEGORIES[i] + COLORS, context.getString(R.string.filter_color),
                    WardrobeFilter.colorOptions(inCategory), section));
        }
        return groups;
    }

    private FilterSheet.Group group(String key, String title, ArrayList<Option> options, String section) {
        ArrayList<String> ticked = new ArrayList<>();
        for (Option option : options) {
            if (chosen(key).contains(option.value)) ticked.add(option.value);
        }
        return new FilterSheet.Group(key, title, null, options, ticked, section);
    }

    private List<String> chosen(String key) {
        List<String> values = chosen.get(key);
        return values == null ? List.of() : values;
    }

    private static boolean hasColor(ItemDto item, List<String> colors) {
        if (item.colors == null) return false;
        for (ColorDto color : item.colors) {
            if (colors.contains(color.code)) return true;
        }
        return false;
    }
}
