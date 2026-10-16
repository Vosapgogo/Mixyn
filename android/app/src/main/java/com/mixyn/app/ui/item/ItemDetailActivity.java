package com.mixyn.app.ui.item;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.ItemTypeDto;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.ColorDots;
import com.mixyn.app.ui.components.MixynBottomBar;
import com.mixyn.app.ui.components.MixynTextField;
import com.mixyn.app.ui.components.OptionsSheet;
import com.mixyn.app.ui.components.OptionsSheet.Mode;
import com.mixyn.app.ui.components.OptionsSheet.Option;
import com.mixyn.app.ui.main.AddMenuSheet;
import com.mixyn.app.ui.main.MainActivity;
import com.mixyn.app.ui.outfit.OutfitActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;

/**
 * The item card: the cleaned photo plus everything known about the piece. The AI filled in type and color;
 * here the user corrects them, edits the photo (size, rotate, mirror), and optionally adds a name, tags and a season.
 * Every change is sent to the backend right away and the card is redrawn from its answer. A new type
 * that a saved outfit has no room for is refused by the backend; the card then says which outfit.
 */
public class ItemDetailActivity extends BaseActivity
        implements EditPhotoSheet.Listener, MixynBottomBar.Listener {

    private static final String EXTRA_ITEM = "item";
    private static final String KEY_TYPE = "type";
    private static final String KEY_TAGS = "tags";
    private static final String KEY_COLOR = "color";
    private static final String KEY_SEASON = "season";
    private static final String KEY_FRONT = "front";
    /** The two answers of the "open or closed" sheet. */
    private static final String FRONT_OPEN = "OPEN";
    private static final String FRONT_CLOSED = "CLOSED";
    private static final int MAX_COLORS = 3; // same limit as the backend

    private final Gson gson = new Gson();
    private MixynApi api;
    private ItemDto item;
    private ImageView photo;
    private View photoFrame;

    public static void start(Context context, ItemDto item) {
        context.startActivity(new Intent(context, ItemDetailActivity.class)
                .putExtra(EXTRA_ITEM, new Gson().toJson(item)));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_detail);
        api = ApiClient.get(this);
        String json = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_ITEM) : getIntent().getStringExtra(EXTRA_ITEM);
        item = gson.fromJson(json, ItemDto.class);
        photo = findViewById(R.id.item_photo);
        photoFrame = findViewById(R.id.item_photo_frame);

        findViewById(R.id.item_back).setOnClickListener(v -> finish());
        findViewById(R.id.item_name).setOnClickListener(v -> editName());
        findViewById(R.id.item_name_edit).setOnClickListener(v -> editName());
        findViewById(R.id.item_edit_photo).setOnClickListener(v ->
                EditPhotoSheet.newInstance(item.scale).show(getSupportFragmentManager(), "edit_photo"));
        MixynBottomBar bottomBar = findViewById(R.id.item_bottom_bar);
        bottomBar.setListener(this);
        findViewById(R.id.item_row_type).setOnClickListener(v -> chooseType());
        findViewById(R.id.item_row_tags).setOnClickListener(v -> chooseTags());
        findViewById(R.id.item_row_color).setOnClickListener(v -> chooseColor());
        findViewById(R.id.item_row_season).setOnClickListener(v -> chooseSeason());
        findViewById(R.id.item_row_front).setOnClickListener(v -> chooseFront());
        findViewById(R.id.item_remove).setOnClickListener(v -> confirmRemove());

        // Each sheet answers under its own key.
        listen(KEY_TYPE, values -> save("type", values.get(0)));
        listen(KEY_TAGS, values -> save("tags", values));
        listen(KEY_SEASON, values -> save("seasons", values));
        listen(KEY_COLOR, values -> save("colors", values));
        listen(KEY_FRONT, values -> save("openFront", FRONT_OPEN.equals(values.get(0))));
        bind();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_ITEM, gson.toJson(item));
    }

    @Override
    protected boolean drawsBehindNavigationBar() {
        return true; // the bottom bar fills that area itself
    }

    // ----- Showing the item -----

    private void bind() {
        Glide.with(this).load(ApiClient.imageUrl(item.imageUrl)).into(photo);
        showPhotoAs(item.scale, 0, false);
        photoFrame.setBackground(null);

        TextView name = findViewById(R.id.item_name);
        boolean hasName = item.name != null && !item.name.isEmpty();
        name.setText(hasName ? item.name : getString(R.string.item_name_hint));
        name.setTextColor(ContextCompat.getColor(this, hasName ? R.color.ink : R.color.ink_muted));

        // Type row: "Blazer ... Outerwear". Without a type, ask for one.
        setRow(R.id.item_type_label, item.typeLabel, R.string.item_type_empty);
        ((TextView) findViewById(R.id.item_type_value))
                .setText(item.category == null ? "" : getString(Categories.label(item.category)));

        setRow(R.id.item_tags_label, joined(item.tags), R.string.item_tags_empty);

        // Colors row: one dot per color, then the names: "Brown, White".
        LinearLayout swatches = findViewById(R.id.item_color_swatches);
        List<ColorDto> colors = item.colors == null ? List.of() : item.colors;
        ColorDots.fill(swatches, colors);
        List<String> colorNames = new ArrayList<>();
        for (ColorDto color : colors) colorNames.add(color.label);
        swatches.setVisibility(colorNames.isEmpty() ? View.GONE : View.VISIBLE);
        setRow(R.id.item_color_label, String.join(getString(R.string.tags_joined_separator), colorNames),
                R.string.item_color_empty);

        setRow(R.id.item_season_label, joined(item.seasons), R.string.item_season_empty);

        // Open or closed front. On an outfit an open piece shows what is worn under it in its opening,
        // so the AI's guess can be corrected here. Only tops and outerwear have such a front.
        boolean hasFront = Categories.TOP.equals(item.category) || Categories.OUTERWEAR.equals(item.category);
        findViewById(R.id.item_row_front).setVisibility(hasFront ? View.VISIBLE : View.GONE);
        findViewById(R.id.item_front_divider).setVisibility(hasFront ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.item_front_label))
                .setText(isOpenFront() ? R.string.item_front_open : R.string.item_front_closed);
    }

    private boolean isOpenFront() {
        return item.opening != null && !item.opening.isEmpty();
    }

    /** ["Work", "Cozy"] → "Work, Cozy"; null or empty → null. */
    @Nullable
    private String joined(@Nullable List<String> values) {
        return values == null || values.isEmpty()
                ? null : String.join(getString(R.string.tags_joined_separator), values);
    }

    private void setRow(int labelId, @Nullable String value, int emptyText) {
        ((TextView) findViewById(labelId)).setText(value == null || value.isEmpty() ? getString(emptyText) : value);
    }

    // ----- Changing the item -----

    private void save(String field, Object value) {
        save(Collections.singletonMap(field, value));
    }

    private void save(Map<String, Object> changes) {
        api.updateItem(item.id, changes).enqueue(itemCallback());
    }

    private ApiCallback<ItemDto> itemCallback() {
        return new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable ItemDto updated) {
                if (updated == null) return;
                item = updated;
                bind();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                bind(); // drop any preview that wasn't saved
                Long outfitId = errorOutfitId();
                if ("type_conflict".equals(code) && outfitId != null) {
                    showTypeConflict(message, outfitId);
                } else {
                    Toast.makeText(ItemDetailActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    /**
     * The new type would leave the piece in a saved outfit where it can't be worn, and that outfit
     * already has a piece in the place the new type takes (a T-shirt turned skirt, in an outfit with
     * jeans). Nothing was changed; the user can open the outfit and change it first.
     */
    private void showTypeConflict(String message, long outfitId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_type_conflict_title)
                .setMessage(message)
                .setNegativeButton(R.string.action_ok, null)
                .setPositiveButton(R.string.item_open_outfit, (d, w) -> OutfitActivity.open(this, outfitId))
                .show();
    }

    // ----- Edit photo (EditPhotoSheet.Listener) -----

    /** Draws the photo at a size / rotation / mirroring without changing the stored picture. */
    private void showPhotoAs(int scale, int rotation, boolean mirror) {
        photo.setRotation(rotation);
        photo.setScaleX(scale / 100f * (mirror ? -1 : 1));
        photo.setScaleY(scale / 100f);
    }

    @Override
    public void onPhotoPreview(int scale, int rotation, boolean mirror) {
        photoFrame.setBackgroundResource(R.drawable.bg_dashed_frame);
        // The picture is mirrored first and then turned, the same order the backend uses on Done.
        showPhotoAs(scale, rotation, mirror);
    }

    @Override
    public void onPhotoEditDone(int scale, int rotation, boolean mirror) {
        Map<String, Object> edit = new HashMap<>();
        edit.put("scale", scale);
        edit.put("rotation", rotation);
        edit.put("mirror", mirror);
        api.editPhoto(item.id, edit).enqueue(itemCallback());
    }

    @Override
    public void onPhotoEditCancelled() {
        bind();
    }

    // ----- Bottom bar (MixynBottomBar.Listener) -----

    @Override
    public void onTabClick(MixynBottomBar.Tab tab) {
        switch (tab) {
            case HOME -> finish(); // the wardrobe is right behind this card
            case CALENDAR, PROFILE -> startActivity(MainActivity.tabIntent(this, tab));
            default -> Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAddClick() {
        new AddMenuSheet().show(getSupportFragmentManager(), "add_menu");
    }

    private void editName() {
        MixynTextField field = new MixynTextField(this, null);
        field.getInput().setHint(R.string.item_name_field_hint);
        field.setValue(item.name == null ? "" : item.name);
        int margin = getResources().getDimensionPixelSize(R.dimen.screen_margin);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(margin, 0, margin, 0);
        box.addView(field);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_name_dialog_title)
                .setView(box)
                .setNegativeButton(R.string.add_item_cancel, null)
                .setPositiveButton(R.string.action_save, (d, w) -> save("name", field.getValue()))
                .show();
    }

    // ----- Remove from wardrobe -----

    /** Removing can't be undone (the photo goes too), so it is asked once more. */
    private void confirmRemove() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.item_remove_title)
                .setMessage(R.string.item_remove_body)
                .setNegativeButton(R.string.add_item_cancel, null)
                .setPositiveButton(R.string.item_remove_confirm, (d, w) -> remove())
                .show();
    }

    private void remove() {
        api.deleteItem(item.id).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable Void nothing) {
                Toast.makeText(ItemDetailActivity.this, R.string.item_removed, Toast.LENGTH_SHORT).show();
                finish(); // back to the closet, which loads its items again
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(ItemDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ----- The choice sheets -----

    private interface OnValues {
        void accept(ArrayList<String> values);
    }

    private void listen(String key, OnValues action) {
        getSupportFragmentManager().setFragmentResultListener(key, this, (k, result) -> {
            ArrayList<String> values = result.getStringArrayList(OptionsSheet.RESULT_VALUES);
            // Tags, colors and seasons may come back empty ("Clear selection"); the type never does.
            boolean mayBeEmpty = !KEY_TYPE.equals(key);
            if (values != null && (!values.isEmpty() || mayBeEmpty)) action.accept(values);
        });
    }

    /** Types come from the backend's fixed list, so the app and the AI always choose from the same one. */
    private void chooseType() {
        Call<List<ItemTypeDto>> call = api.itemTypes();
        call.enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable List<ItemTypeDto> types) {
                if (types == null) return;
                ArrayList<Option> options = new ArrayList<>();
                for (ItemTypeDto t : types) {
                    String slot = getString(Categories.label(t.category));
                    if (t.alsoCategory != null) { // a cardigan: "Top · Outerwear"
                        slot = getString(R.string.category_pair, slot, getString(Categories.label(t.alsoCategory)));
                    }
                    options.add(new Option(t.code, t.label, slot, 0));
                }
                show(KEY_TYPE, R.string.item_type_title, Mode.SEARCH_LIST, options,
                        item.type == null ? new ArrayList<>() : new ArrayList<>(List.of(item.type)),
                        getString(R.string.item_type_search_hint));
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(ItemDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void chooseTags() {
        show(KEY_TAGS, R.string.item_tags_title, Mode.CHIPS_MULTI,
                optionsFrom(R.array.tag_names, R.array.tag_colors),
                item.tags == null ? new ArrayList<>() : new ArrayList<>(item.tags), null);
    }

    /** Colors also come from the backend's palette; up to MAX_COLORS can be selected. */
    private void chooseColor() {
        api.itemColors().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable List<ColorDto> palette) {
                if (palette == null) return;
                ArrayList<Option> options = new ArrayList<>();
                for (ColorDto c : palette) options.add(new Option(c.code, c.label, null, Color.parseColor(c.hex)));
                ArrayList<String> selected = new ArrayList<>();
                if (item.colors != null) for (ColorDto c : item.colors) selected.add(c.code);
                OptionsSheet.newInstance(KEY_COLOR, getString(R.string.item_color_title), Mode.CHIPS_MULTI,
                        options, selected, null, MAX_COLORS).show(getSupportFragmentManager(), KEY_COLOR);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(ItemDetailActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void chooseSeason() {
        show(KEY_SEASON, R.string.item_season_title, Mode.CHIPS_MULTI,
                optionsFrom(R.array.season_names, 0),
                item.seasons == null ? new ArrayList<>() : new ArrayList<>(item.seasons), null);
    }

    private void chooseFront() {
        ArrayList<Option> options = new ArrayList<>();
        options.add(new Option(FRONT_CLOSED, getString(R.string.item_front_closed), null, 0));
        options.add(new Option(FRONT_OPEN, getString(R.string.item_front_open), null, 0));
        show(KEY_FRONT, R.string.item_front_title, Mode.CHIPS_SINGLE, options,
                new ArrayList<>(List.of(isOpenFront() ? FRONT_OPEN : FRONT_CLOSED)), null);
    }

    private void show(String key, int title, Mode mode, ArrayList<Option> options, ArrayList<String> selected,
            @Nullable String searchHint) {
        OptionsSheet.newInstance(key, getString(title), mode, options, selected, searchHint)
                .show(getSupportFragmentManager(), key);
    }

    /** Builds options from a names array and (optionally) the matching colors array in arrays.xml. */
    private ArrayList<Option> optionsFrom(int namesArray, int colorsArray) {
        String[] names = getResources().getStringArray(namesArray);
        TypedArray colors = colorsArray == 0 ? null : getResources().obtainTypedArray(colorsArray);
        ArrayList<Option> options = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            options.add(new Option(names[i], names[i], null, colors == null ? 0 : colors.getColor(i, 0)));
        }
        if (colors != null) colors.recycle();
        return options;
    }
}
