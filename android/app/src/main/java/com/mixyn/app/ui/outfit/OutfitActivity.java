package com.mixyn.app.ui.outfit;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.ColorDots;
import com.mixyn.app.ui.components.ItemNames;
import com.mixyn.app.ui.components.OptionsSheet;
import com.mixyn.app.ui.components.OptionsSheet.Mode;
import com.mixyn.app.ui.components.OptionsSheet.Option;
import com.mixyn.app.ui.main.MainActivity;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * An outfit's card: the picture of the look, the clothes in it, its colors (taken from the clothes),
 * its category and its seasons.
 *
 * It opens in one of two ways. From the canvas (Next) it shows the look that was just put together,
 * "Your fit for today": nothing is stored yet, the category and seasons chosen here go to the backend
 * together with the picture when the user taps Save. From the Outfits tab it shows a saved outfit:
 * every change is sent right away and the card is redrawn from the answer, and the outfit can be deleted.
 */
public class OutfitActivity extends BaseActivity {

    private static final String EXTRA_PIECES = "pieces";
    private static final String EXTRA_PICTURE = "picture";
    private static final String EXTRA_WEATHER = "weather";
    private static final String EXTRA_OUTFIT = "outfit";
    private static final String STATE_TAGS = "tags";
    private static final String STATE_SEASONS = "seasons";
    private static final String KEY_CATEGORY = "outfit_category";
    private static final String KEY_SEASON = "outfit_season";

    private static final DateTimeFormatter WEEKDAY_AND_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    private final Gson gson = new Gson();
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private MixynApi api;
    /** The saved outfit, or null while this is still a look from the canvas. */
    @Nullable
    private OutfitDto outfit;
    /** The clothes of a look that isn't saved yet, and the file with its picture. */
    private List<OutfitDto.Piece> draftPieces = new ArrayList<>();
    @Nullable
    private String draftPicture;
    /** What the user chose for a look that isn't saved yet. */
    private ArrayList<String> draftTags = new ArrayList<>();
    private ArrayList<String> draftSeasons = new ArrayList<>();
    private MaterialButton saveButton;

    /**
     * Opens the look that is on the canvas.
     *
     * @param weather the weather right now, like "4° cloudy", or null when it isn't known
     * @param picture the look as a PNG file (see OutfitPicture)
     */
    public static void startNew(Context context, List<OutfitDto.Piece> pieces, @Nullable String weather, File picture) {
        context.startActivity(new Intent(context, OutfitActivity.class)
                .putExtra(EXTRA_PIECES, new Gson().toJson(pieces))
                .putExtra(EXTRA_WEATHER, weather)
                .putExtra(EXTRA_PICTURE, picture.getAbsolutePath()));
    }

    /** Opens a saved outfit. */
    public static void start(Context context, OutfitDto outfit) {
        context.startActivity(new Intent(context, OutfitActivity.class)
                .putExtra(EXTRA_OUTFIT, new Gson().toJson(outfit)));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outfit);
        api = ApiClient.get(this);
        saveButton = findViewById(R.id.outfit_save);

        String saved = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_OUTFIT) : getIntent().getStringExtra(EXTRA_OUTFIT);
        if (saved != null) {
            outfit = gson.fromJson(saved, OutfitDto.class);
        } else {
            draftPieces = gson.fromJson(getIntent().getStringExtra(EXTRA_PIECES),
                    new TypeToken<List<OutfitDto.Piece>>() { }.getType());
            draftPicture = getIntent().getStringExtra(EXTRA_PICTURE);
            if (savedInstanceState != null) {
                draftTags = savedInstanceState.getStringArrayList(STATE_TAGS);
                draftSeasons = savedInstanceState.getStringArrayList(STATE_SEASONS);
            }
        }

        findViewById(R.id.outfit_back).setOnClickListener(v -> finish());
        findViewById(R.id.outfit_share).setOnClickListener(v ->
                Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show());
        findViewById(R.id.outfit_row_category).setOnClickListener(v -> choose(KEY_CATEGORY,
                R.string.outfit_category_title, R.array.tag_names, R.array.tag_colors, tags()));
        findViewById(R.id.outfit_row_season).setOnClickListener(v -> choose(KEY_SEASON,
                R.string.item_season_title, R.array.season_names, 0, seasons()));
        findViewById(R.id.outfit_delete).setOnClickListener(v -> confirmDelete());
        saveButton.setOnClickListener(v -> save());

        // Each sheet answers under its own key. An empty answer ("Clear selection") is a choice too.
        listen(KEY_CATEGORY, values -> change("tags", inListOrder(values, R.array.tag_names)));
        listen(KEY_SEASON, values -> change("seasons", inListOrder(values, R.array.season_names)));

        showPicture();
        bind();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (outfit != null) {
            outState.putString(EXTRA_OUTFIT, gson.toJson(outfit));
        } else {
            outState.putStringArrayList(STATE_TAGS, draftTags);
            outState.putStringArrayList(STATE_SEASONS, draftSeasons);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        background.shutdown();
    }

    // ----- Showing the outfit -----

    private void showPicture() {
        ImageView picture = findViewById(R.id.outfit_picture);
        if (outfit != null) {
            Glide.with(this).load(ApiClient.imageUrl(outfit.imageUrl)).into(picture);
        } else if (draftPicture != null) {
            picture.setImageBitmap(BitmapFactory.decodeFile(draftPicture));
        }
    }

    private void bind() {
        boolean saved = outfit != null;
        ((TextView) findViewById(R.id.outfit_title))
                .setText(saved ? R.string.outfit_saved_title : R.string.outfit_new_title);
        ((TextView) findViewById(R.id.outfit_subtitle)).setText(saved ? savedLine() : todayLine());

        LinearLayout list = findViewById(R.id.outfit_pieces);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (OutfitDto.Piece piece : pieces()) {
            View row = inflater.inflate(R.layout.item_outfit_piece, list, false);
            row.findViewById(R.id.piece_divider).setVisibility(list.getChildCount() == 0 ? View.GONE : View.VISIBLE);
            Glide.with(this).load(ApiClient.imageUrl(piece.item.imageUrl))
                    .into((ImageView) row.findViewById(R.id.piece_image));
            String name = ItemNames.of(this, piece.item);
            String slot = getString(slotLabel(piece.slot));
            ((TextView) row.findViewById(R.id.piece_name)).setText(name);
            // A piece with no name, type or color is called by its place; saying it twice adds nothing.
            ((TextView) row.findViewById(R.id.piece_slot)).setText(name.equals(slot) ? "" : slot);
            list.addView(row);
        }

        // Colors row: one dot per color of the pieces, then the names: "Brown, White".
        List<ColorDto> colors = colors();
        LinearLayout swatches = findViewById(R.id.outfit_color_swatches);
        ColorDots.fill(swatches, colors);
        swatches.setVisibility(colors.isEmpty() ? View.GONE : View.VISIBLE);
        List<String> colorNames = new ArrayList<>();
        for (ColorDto color : colors) colorNames.add(color.label);
        setRow(R.id.outfit_color_label, colorNames, R.string.outfit_colors_empty);

        setRow(R.id.outfit_category_label, tags(), R.string.outfit_category_empty);
        setRow(R.id.outfit_season_label, seasons(), R.string.item_season_empty);

        findViewById(R.id.outfit_delete).setVisibility(saved ? View.VISIBLE : View.GONE);
        findViewById(R.id.outfit_actions).setVisibility(saved ? View.GONE : View.VISIBLE);
    }

    /** "Tuesday, 29 September · 4° cloudy"; just the day when the weather isn't known. */
    private String todayLine() {
        String today = LocalDate.now().format(WEEKDAY_AND_DAY);
        String weather = getIntent().getStringExtra(EXTRA_WEATHER);
        return weather == null ? today : getString(R.string.outfit_date_weather, today, weather);
    }

    /** "Saved 12 October". */
    private String savedLine() {
        LocalDate savedOn = Instant.parse(outfit.createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
        return getString(R.string.outfit_saved_on, savedOn.format(DAY));
    }

    private List<OutfitDto.Piece> pieces() {
        return outfit != null ? outfit.pieces : draftPieces;
    }

    private ArrayList<String> tags() {
        return outfit == null ? draftTags : outfit.tags == null ? new ArrayList<>() : new ArrayList<>(outfit.tags);
    }

    private ArrayList<String> seasons() {
        return outfit == null ? draftSeasons
                : outfit.seasons == null ? new ArrayList<>() : new ArrayList<>(outfit.seasons);
    }

    /** Every color of the outfit once, in the order of its pieces and each piece's main color first. */
    private List<ColorDto> colors() {
        Map<String, ColorDto> byCode = new LinkedHashMap<>();
        for (OutfitDto.Piece piece : pieces()) {
            if (piece.item.colors == null) continue;
            for (ColorDto color : piece.item.colors) byCode.putIfAbsent(color.code, color);
        }
        return new ArrayList<>(byCode.values());
    }

    /** Where a piece is worn: the under layer has a name of its own, the rest are the categories. */
    @StringRes
    private static int slotLabel(String slot) {
        return OutfitDto.Piece.UNDER.equals(slot) ? R.string.canvas_under : Categories.label(slot);
    }

    private void setRow(int labelId, List<String> values, @StringRes int emptyText) {
        ((TextView) findViewById(labelId)).setText(values.isEmpty()
                ? getString(emptyText) : String.join(getString(R.string.tags_joined_separator), values));
    }

    // ----- Category and seasons -----

    private interface OnValues {
        void accept(ArrayList<String> values);
    }

    private void listen(String key, OnValues action) {
        getSupportFragmentManager().setFragmentResultListener(key, this, (k, result) -> {
            ArrayList<String> values = result.getStringArrayList(OptionsSheet.RESULT_VALUES);
            if (values != null) action.accept(values);
        });
    }

    /** The chosen values in the order of their list, not the order they were tapped in. */
    private ArrayList<String> inListOrder(ArrayList<String> chosen, int namesArray) {
        ArrayList<String> ordered = new ArrayList<>();
        for (String name : getResources().getStringArray(namesArray)) {
            if (chosen.contains(name)) ordered.add(name);
        }
        return ordered;
    }

    /** Opens a sheet of chips built from a names array and (optionally) the matching colors array. */
    private void choose(String key, @StringRes int title, int namesArray, int colorsArray, ArrayList<String> chosen) {
        String[] names = getResources().getStringArray(namesArray);
        TypedArray dots = colorsArray == 0 ? null : getResources().obtainTypedArray(colorsArray);
        ArrayList<Option> options = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            options.add(new Option(names[i], names[i], null, dots == null ? 0 : dots.getColor(i, 0)));
        }
        if (dots != null) dots.recycle();
        OptionsSheet.newInstance(key, getString(title), Mode.CHIPS_MULTI, options, chosen, null)
                .show(getSupportFragmentManager(), key);
    }

    /** A saved outfit is changed on the backend; a look that isn't saved yet just remembers the choice. */
    private void change(String field, ArrayList<String> values) {
        if (outfit == null) {
            if ("tags".equals(field)) draftTags = values;
            else draftSeasons = values;
            bind();
            return;
        }
        api.updateOutfit(outfit.id, Collections.singletonMap(field, values)).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable OutfitDto updated) {
                if (updated == null) return;
                outfit = updated;
                bind();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(OutfitActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ----- Save -----

    /**
     * Sends the look to the backend: its picture, which piece is worn where, and what was chosen on
     * this card. On success the new outfit is in the Outfits tab, so that is where the user goes.
     */
    private void save() {
        if (draftPicture == null) return;
        setSaving(true);
        String path = draftPicture;
        // Reading the picture file is slow work, so it stays off the main (UI) thread.
        background.execute(() -> {
            byte[] png = OutfitPicture.read(path);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (png == null) {
                    setSaving(false);
                    Toast.makeText(this, R.string.error_outfit_picture, Toast.LENGTH_LONG).show();
                    return;
                }
                MultipartBody.Part picture = MultipartBody.Part.createFormData("picture", "outfit.png",
                        RequestBody.create(MediaType.parse("image/png"), png));
                Map<String, Object> body = new HashMap<>();
                body.put("look", lookIds());
                body.put("tags", draftTags);
                body.put("seasons", draftSeasons);
                long userId = new SessionStore(this).getUserId();
                api.saveOutfit(userId, picture, body).enqueue(new ApiCallback<>(this) {
                    @Override
                    protected void onSuccess(@Nullable OutfitDto savedOutfit) {
                        Toast.makeText(OutfitActivity.this, R.string.outfit_saved_toast, Toast.LENGTH_SHORT).show();
                        startActivity(MainActivity.outfitsIntent(OutfitActivity.this));
                    }

                    @Override
                    protected void onError(@Nullable String code, @NonNull String message) {
                        setSaving(false);
                        Toast.makeText(OutfitActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    /** Which item is worn where, in the shape the backend expects: {"topId": 5, …, "accessoryIds": [8]}. */
    private Map<String, Object> lookIds() {
        Map<String, Object> ids = new HashMap<>();
        List<Long> accessoryIds = new ArrayList<>();
        for (OutfitDto.Piece piece : draftPieces) {
            switch (piece.slot) {
                case OutfitDto.Piece.TOP -> ids.put("topId", piece.item.id);
                case OutfitDto.Piece.BOTTOM -> ids.put("bottomId", piece.item.id);
                case OutfitDto.Piece.SHOES -> ids.put("shoesId", piece.item.id);
                case OutfitDto.Piece.OUTERWEAR -> ids.put("outerwearId", piece.item.id);
                case OutfitDto.Piece.UNDER -> ids.put("underId", piece.item.id);
                case OutfitDto.Piece.BAG -> ids.put("bagId", piece.item.id);
                default -> accessoryIds.add(piece.item.id);
            }
        }
        ids.put("accessoryIds", accessoryIds);
        return ids;
    }

    private void setSaving(boolean saving) {
        saveButton.setEnabled(!saving);
        saveButton.setText(saving ? R.string.outfit_saving : R.string.action_save);
    }

    // ----- Delete -----

    /** Deleting can't be undone (the picture goes too), so it is asked once more. */
    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.outfit_delete_title)
                .setMessage(R.string.outfit_delete_body)
                .setNegativeButton(R.string.add_item_cancel, null)
                .setPositiveButton(R.string.outfit_delete_confirm, (d, w) -> delete())
                .show();
    }

    private void delete() {
        if (outfit == null) return;
        api.deleteOutfit(outfit.id).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable Void nothing) {
                Toast.makeText(OutfitActivity.this, R.string.outfit_deleted, Toast.LENGTH_SHORT).show();
                finish(); // back to the list, which loads its outfits again
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(OutfitActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
