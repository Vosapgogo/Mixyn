package com.mixyn.app.ui.components;

import android.content.Context;

import com.mixyn.app.R;
import com.mixyn.app.api.dto.ItemDto;

import java.util.Locale;

/** What a piece is called wherever it is listed by name (the weather tip, an outfit's card). */
public final class ItemNames {

    private ItemNames() {
    }

    /**
     * The name the user gave the piece; without one, its main color and type ("Khaki blazer"), or as
     * much of that as is known.
     */
    public static String of(Context context, ItemDto item) {
        if (item.name != null && !item.name.isEmpty()) return item.name;
        if (item.typeLabel == null) return context.getString(Categories.label(item.category));
        if (item.colorName == null) return item.typeLabel;
        return context.getString(R.string.piece_name, item.colorName, item.typeLabel.toLowerCase(Locale.ROOT));
    }
}
