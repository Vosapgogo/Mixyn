package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.api.dto.WeatherDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.additem.AddItemActivity;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.FilterSheet;
import com.mixyn.app.ui.components.ItemNames;
import com.mixyn.app.ui.components.OptionsSheet;
import com.mixyn.app.ui.main.WardrobeFilter;
import com.mixyn.app.ui.outfit.OutfitActivity;
import com.mixyn.app.ui.outfit.OutfitPicture;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Create outfit. The wardrobe is loaded once and split by category. The base of every outfit is three
 * slots (top, bottom, shoes), each a pager over its category. Around them lie optional layers, hidden
 * until they are put on: a piece worn under the top (its collar and hem show around the top), outerwear
 * (only its left half is drawn, like an open jacket), a bag, and accessories, each in its own spot on
 * the outfit. A piece is worn in one place at a time: a layer never offers the piece that is the top
 * right now, and the top slot never offers a piece that is on as the under layer or the outerwear.
 *
 * A new look starts on a random piece in each slot, with no layers on. A slot the closet has nothing
 * for shows a dashed placeholder instead ("Add shoes to complete the look") that opens Add clothes.
 * Next needs all three: an outfit is saved only with a top, bottoms and shoes. A dress is the one
 * exception: it hangs over the place of the bottoms like a long coat, and the bottom slot then starts
 * on an empty page, from which the user may still swipe to leggings or jeans to wear under it.
 *
 * The filter in the header narrows every carousel to the chosen colors and types (a type only its own
 * carousel); a slot it leaves empty says "No shoes match your filter".
 *
 * A layer is chosen in "focus": tapping Under layer, Outerwear, Bag or Accessories locks the rest of the outfit, so
 * swipes change only that layer, until the user taps Done. If the closet has nothing for a layer, the
 * canvas offers to go and add a piece. The round Ask AI button sends an optional hint to the backend
 * stylist, which may change the slots and all the layers at once. The weather chip and Next offer the
 * layers the weather calls for; after that Next takes the look to its card, where it is saved.
 *
 * Opened with Edit on a saved outfit, the canvas starts with that outfit on, and Next takes the changed
 * look to a card where it can be saved as new or update the outfit. A piece that can't be put back where
 * it was (its type changed since) is left out, and the user is told.
 */
public class OutfitCanvasActivity extends BaseActivity {

    private static final float NEIGHBOR_ALPHA = 0.25f;
    private static final float NEIGHBOR_SCALE = 0.8f;
    /** Outerwear is drawn a touch larger than the top it covers, like a jacket worn over a sweater. */
    private static final float OUTERWEAR_SCALE = 1.08f;
    /** Shoes are drawn smaller than the slot allows, so they sit under the trousers instead of on them. */
    private static final float SHOES_SCALE = 0.7f;
    /**
     * Drawing order inside the body frame. Normally the bottom is drawn over the top's hem (tucked in);
     * with a piece under the top, the order is bottom, under layer, top, so its hem hangs over the bottom,
     * and a dress, too, is drawn over whatever is worn under it.
     * Outerwear is always over all of them.
     */
    private static final float Z_TUCKED_BOTTOM = 1f;
    private static final float Z_OUTERWEAR = 2f;
    /** A top that takes the place of the bottoms as well. */
    private static final String DRESS = "DRESS";
    /** Everything that can't be changed while one layer is being chosen is faded to this. */
    private static final float LOCKED_ALPHA = 0.45f;
    /** Where the bag hangs: by the hand, beside the bottom. Same units as AccessorySpot. */
    private static final float BAG_LEFT = 0.66f, BAG_RIGHT = 0.98f, BAG_TOP = 0.50f, BAG_BOTTOM = 0.76f;

    /** The accessories the weather tip offers, by item type. In the cold it offers outerwear as well. */
    private static final List<String> COLD_TYPES = List.of("BEANIE", "SCARF", "GLOVES");
    private static final List<String> HOT_TYPES = List.of("CAP", "HAT", "HEADSCARF");
    /** The weather tip's key for "put outerwear on"; every other key is an accessory type. */
    private static final String TIP_OUTERWEAR = "OUTERWEAR";

    private static final String KEY_ACCESSORIES = "canvas_accessories";
    private static final String KEY_FILTER = "canvas_filter";
    private static final String EXTRA_EDIT = "edit";

    /** One optional layer over the outfit: its pager and whether it is part of the outfit right now. */
    private static final class Layer {
        final ViewPager2 pager;
        /** True for layers as wide as the canvas, whose neighbors peek in at the sides while in focus. */
        final boolean wide;
        /** For an accessory layer, its place on the outfit; null for outerwear and the bag. */
        @Nullable
        final AccessorySpot spot;
        boolean on;

        Layer(ViewPager2 pager, boolean wide, @Nullable AccessorySpot spot) {
            this.pager = pager;
            this.wide = wide;
            this.spot = spot;
            pager.setUserInputEnabled(false); // swipes reach a layer only while it is in focus
        }
    }

    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final Random random = new Random();
    private MixynApi api;
    private long userId;
    private ViewPager2 topPager;
    private ViewPager2 bottomPager;
    private ViewPager2 shoesPager;
    private Layer under;
    private Layer outerwear;
    private Layer bag;
    /** How much of the body frame (top + bottom) the top takes. */
    private float topShare;
    /** Share of the screen width left on each side of a piece, where its neighbors peek in. */
    private float sidePeek;
    /** What the top, bottom and shoes slots show instead of their pager while they have nothing to show. */
    private final Map<ViewPager2, SlotPlaceholderView> placeholders = new HashMap<>();
    /** The colors and types chosen in the filter; only matching pieces are in the carousels. */
    private final WardrobeFilter filter = new WardrobeFilter();
    /** The whole wardrobe as it was loaded, before the filter. */
    private List<ItemDto> wardrobe = List.of();
    /** The slots and layers whose pieces the filter hides all of right now. */
    private final Set<ViewPager2> filteredOut = new HashSet<>();
    private boolean accessoriesFilteredOut;
    private final Map<AccessorySpot, Layer> accessorySpots = new EnumMap<>(AccessorySpot.class);
    private final List<Layer> layers = new ArrayList<>();
    private ImageButton askAi;
    private TextView weatherChip;
    /** The layers the user is choosing right now; empty = the normal canvas. */
    private final Set<Layer> focused = new HashSet<>();
    private OnBackPressedCallback leaveFocusOnBack;
    /**
     * "Add to wardrobe" from a missing-piece dialog, or the + in an empty slot: when the user comes back
     * with a new piece, the wardrobe is loaded again and the piece is put on (see showAdded).
     */
    private final ActivityResultLauncher<Intent> addPiece =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return; // nothing was added
                addedItemId = result.getData().getLongExtra(AddItemActivity.RESULT_ITEM_ID, -1);
                loadWardrobe();
            });
    /** The piece just added, until the reloaded wardrobe has it; -1 otherwise. */
    private long addedItemId = -1;
    /** The layer whose tile led to "Add to wardrobe", or null (accessories, the weather tip). */
    @Nullable
    private Layer wantedLayer;
    /** False until the wardrobe has arrived: before that, "the closet has no bag" would be a guess. */
    private boolean wardrobeLoaded;
    /** The saved outfit being changed (Edit), or null for a new look. */
    @Nullable
    private OutfitDto editing;
    /** Whether that outfit has been put on the canvas yet (once, when the wardrobe first arrives). */
    private boolean editingShown;

    /** Every top in the wardrobe. The top slot offers them all except the ones worn as a layer. */
    private List<ItemDto> topPieces = new ArrayList<>();
    /** Every bottom in the wardrobe; under a dress the bottom slot offers them after an empty page. */
    private List<ItemDto> bottomPieces = new ArrayList<>();
    /** The bottom that was on when a dress became the top: it comes back with the next top that isn't a dress. */
    @Nullable
    private ItemDto bottomBeforeDress;
    /** The thin tops that can also be worn under another top (the backend marks them). */
    private final List<ItemDto> underPieces = new ArrayList<>();
    /** Every piece that can be worn as outerwear: jackets and coats, and cardigans (which are tops too). */
    private final List<ItemDto> outerwearPieces = new ArrayList<>();
    /** Every accessory in the wardrobe, and the types the user chose to wear ("SCARF", "CAP"…). */
    private final List<ItemDto> accessories = new ArrayList<>();
    private final Set<String> wornTypes = new LinkedHashSet<>();

    @Nullable
    private WeatherDto weather;
    /** Whether the open weather tip interrupted Next (then "Not now" goes on to the next screen). */
    private boolean tipFromNext;
    /** Next asks about the weather only once per visit; after that it just goes on. */
    private boolean weatherTipOffered;

    /**
     * The stylist's memory for this visit to the canvas: each request with the outfit it produced
     * ({"hint": ..., "look": {topId…}}). Sent along with every new request; gone when the screen closes.
     */
    private final List<Map<String, Object>> conversation = new ArrayList<>();
    private static final int MAX_REMEMBERED_REQUESTS = 6; // same limit as the backend

    public static void start(Context context) {
        context.startActivity(new Intent(context, OutfitCanvasActivity.class));
    }

    /** Opens the canvas with this saved outfit on, to change it. */
    public static void edit(Context context, OutfitDto outfit) {
        context.startActivity(new Intent(context, OutfitCanvasActivity.class)
                .putExtra(EXTRA_EDIT, new Gson().toJson(outfit)));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outfit_canvas);
        api = ApiClient.get(this);
        userId = new SessionStore(this).getUserId();
        String toEdit = getIntent().getStringExtra(EXTRA_EDIT);
        if (toEdit != null) editing = new Gson().fromJson(toEdit, OutfitDto.class);

        topPager = findViewById(R.id.canvas_top);
        bottomPager = findViewById(R.id.canvas_bottom);
        shoesPager = findViewById(R.id.canvas_shoes);
        askAi = findViewById(R.id.canvas_ask_ai);
        weatherChip = findViewById(R.id.canvas_weather);

        topShare = ResourcesCompat.getFloat(getResources(), R.dimen.canvas_top_share);
        sidePeek = (1 - ResourcesCompat.getFloat(getResources(), R.dimen.canvas_piece_share)) / 2;

        placeholders.put(topPager, findViewById(R.id.canvas_top_placeholder));
        placeholders.put(bottomPager, findViewById(R.id.canvas_bottom_placeholder));
        placeholders.put(shoesPager, findViewById(R.id.canvas_shoes_placeholder));
        // The + in an empty slot: add a piece, then come straight back to the outfit with it in that slot.
        // A slot the filter emptied opens the filter instead.
        placeholders.forEach((slot, placeholder) -> placeholder.setOnClickListener(v -> {
            if (filteredOut.contains(slot)) {
                openFilter();
                return;
            }
            wantedLayer = null;
            addPiece.launch(AddItemActivity.forResult(this));
        }));

        under = new Layer(findViewById(R.id.canvas_under), true, null);
        outerwear = new Layer(findViewById(R.id.canvas_outerwear), true, null);
        bag = new Layer(findViewById(R.id.canvas_bag), false, null);
        bottomPager.setTranslationZ(Z_TUCKED_BOTTOM);
        outerwear.pager.setTranslationZ(Z_OUTERWEAR);
        // The collar of the piece under the top is drawn a little above the frame, and a dress in the top's
        // slot reaches down over the bottom's.
        ((ViewGroup) under.pager.getChildAt(0)).setClipChildren(false);
        ((ViewGroup) topPager.getChildAt(0)).setClipChildren(false);
        layers.add(under);
        layers.add(outerwear);
        layers.add(bag);
        for (AccessorySpot spot : AccessorySpot.values()) {
            Layer layer = new Layer(findViewById(spot.pagerId), spot.right - spot.left == 1f, spot);
            accessorySpots.put(spot, layer);
            layers.add(layer);
        }
        View stage = findViewById(R.id.canvas_layers);
        whenLaidOut(stage, this::placeLayers);

        // The under layer and the outerwear follow every change of the top: they are drawn around it,
        // and they never offer the piece that has just become the top.
        topPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) return;
                offerBottoms();
                offerLayersExcept(currentItem(topPager));
                alignToTop();
            }
        });

        // A piece that is on as the under layer or the outerwear is no longer offered as the top.
        ViewPager2.OnPageChangeCallback wornChanged = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) offerTops();
            }
        };
        under.pager.registerOnPageChangeCallback(wornChanged);
        outerwear.pager.registerOnPageChangeCallback(wornChanged);

        findViewById(R.id.canvas_under_button).setOnClickListener(v -> chooseLayer(under,
                R.string.canvas_focus_under, R.string.missing_under_title, R.string.missing_under_body,
                R.string.canvas_no_match_under));
        findViewById(R.id.canvas_outerwear_button).setOnClickListener(v -> chooseLayer(outerwear,
                R.string.canvas_focus_outerwear, R.string.missing_outerwear_title, R.string.missing_outerwear_body,
                R.string.canvas_no_match_outerwear));
        findViewById(R.id.canvas_bag_button).setOnClickListener(v -> chooseLayer(bag,
                R.string.canvas_focus_bag, R.string.missing_bag_title, R.string.missing_bag_body,
                R.string.canvas_no_match_bag));
        findViewById(R.id.canvas_accessory_button).setOnClickListener(v -> chooseAccessoryTypes());
        getSupportFragmentManager().setFragmentResultListener(KEY_ACCESSORIES, this, (key, result) ->
                wear(result.getStringArrayList(OptionsSheet.RESULT_VALUES)));
        findViewById(R.id.canvas_focus_done).setOnClickListener(v -> leaveFocus());
        findViewById(R.id.canvas_focus_remove).setOnClickListener(v -> removeFocusedLayers());

        // Back (the arrow or the system gesture) first leaves the layer being chosen, then the screen.
        leaveFocusOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                leaveFocus();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, leaveFocusOnBack);
        findViewById(R.id.canvas_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        askAi.setOnClickListener(v -> AskAiSheet.newInstance(lastRequest())
                .show(getSupportFragmentManager(), AskAiSheet.REQUEST_KEY));
        getSupportFragmentManager().setFragmentResultListener(AskAiSheet.REQUEST_KEY, this, (key, result) -> {
            if (result.getBoolean(AskAiSheet.RESULT_START_OVER)) conversation.clear();
            generate(result.getString(AskAiSheet.RESULT_HINT, ""));
        });

        findViewById(R.id.canvas_filter).setOnClickListener(v -> openFilter());
        getSupportFragmentManager().setFragmentResultListener(KEY_FILTER, this, (key, result) -> {
            filter.read(result);
            filterChanged();
        });

        weatherChip.setOnClickListener(v -> offerWeatherTip(false));
        findViewById(R.id.canvas_next).setOnClickListener(v -> {
            if (!wardrobeLoaded || askForMissingBase()) return;
            if (weatherTipOffered) goNext();
            else offerWeatherTip(true);
        });
        getSupportFragmentManager().setFragmentResultListener(WeatherTipSheet.REQUEST_KEY, this, (key, result) -> {
            if (result.getBoolean(WeatherTipSheet.RESULT_ADD)) {
                addFromWeatherTip(result.getStringArrayList(WeatherTipSheet.RESULT_KEYS));
            } else if (tipFromNext) {
                goNext();
            }
        });

        loadWardrobe();
        loadWeather();
    }

    @Override
    protected void onDestroy() {
        background.shutdown();
        super.onDestroy();
    }

    /**
     * Next: the look goes to its card ("Your fit for today"), where it can be saved. The card gets the
     * pieces that are on and a picture of the outfit as it stands on the canvas.
     */
    private void goNext() {
        List<OutfitDto.Piece> pieces = wornPieces();
        if (pieces.isEmpty()) return; // the wardrobe hasn't arrived, or there is nothing to style
        Bitmap drawn = drawOutfit();
        String weatherNow = weather == null ? null
                : getString(R.string.outfit_weather, weather.temperature, weather.sky);
        View next = findViewById(R.id.canvas_next);
        next.setEnabled(false);
        // Cutting the picture down to the outfit and writing the file is slow work: off the main thread.
        background.execute(() -> {
            File picture = OutfitPicture.save(this, drawn);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                next.setEnabled(true);
                if (picture == null) {
                    Toast.makeText(this, R.string.error_outfit_picture, Toast.LENGTH_LONG).show();
                } else if (editing != null) {
                    OutfitActivity.startEdited(this, editing, pieces, weatherNow, picture);
                } else {
                    OutfitActivity.startNew(this, pieces, weatherNow, picture);
                }
            });
        });
    }

    /**
     * An outfit can only be saved with a top, bottoms and shoes, or a dress and shoes (the backend
     * refuses it otherwise). If
     * the canvas lacks any of them, says which and offers to go and add one: it then lands in its empty
     * slot. Returns false when nothing is missing.
     */
    private boolean askForMissingBase() {
        List<ViewPager2> missing = new ArrayList<>();
        if (currentItem(topPager) == null) missing.add(topPager);
        if (currentItem(bottomPager) == null && !isDress(currentItem(topPager))) missing.add(bottomPager);
        if (currentItem(shoesPager) == null) missing.add(shoesPager);
        if (missing.isEmpty()) return false;
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.canvas_incomplete_title)
                .setNegativeButton(R.string.canvas_incomplete_later, null);
        // The closet has them, but the filter hides them: offer to clear it. Otherwise offer to add one.
        List<ViewPager2> hidden = new ArrayList<>(missing);
        hidden.retainAll(filteredOut);
        if (!hidden.isEmpty()) {
            dialog.setMessage(getString(R.string.canvas_incomplete_filtered, pieceNames(hidden, true)))
                    .setPositiveButton(R.string.canvas_filter_clear, (d, which) -> clearFilter());
        } else {
            dialog.setMessage(getString(R.string.canvas_incomplete_body, pieceNames(missing, false)))
                    .setPositiveButton(R.string.missing_add, (d, which) -> {
                        wantedLayer = null;
                        addPiece.launch(AddItemActivity.forResult(this));
                    });
        }
        dialog.show();
        return true;
    }

    /** "a top, bottoms and shoes"; in the plural for the ones the filter hides: "tops and shoes". */
    private String pieceNames(List<ViewPager2> slots, boolean plural) {
        List<String> names = new ArrayList<>();
        for (ViewPager2 slot : slots) {
            int name = slot == topPager ? (plural ? R.string.canvas_filtered_top : R.string.canvas_incomplete_top)
                    : slot == bottomPager ? (plural ? R.string.canvas_filtered_bottom : R.string.canvas_incomplete_bottom)
                    : (plural ? R.string.canvas_filtered_shoes : R.string.canvas_incomplete_shoes);
            names.add(getString(name));
        }
        if (names.size() == 1) return names.get(0);
        return String.join(getString(R.string.tags_joined_separator), names.subList(0, names.size() - 1))
                + getString(R.string.joined_last_separator) + names.get(names.size() - 1);
    }

    /** Every piece that is on right now with its place, in the order an outfit's card lists them. */
    private List<OutfitDto.Piece> wornPieces() {
        List<OutfitDto.Piece> pieces = new ArrayList<>();
        addPiece(pieces, OutfitDto.Piece.TOP, currentItem(topPager));
        addPiece(pieces, OutfitDto.Piece.BOTTOM, currentItem(bottomPager));
        addPiece(pieces, OutfitDto.Piece.SHOES, currentItem(shoesPager));
        if (outerwear.on) addPiece(pieces, OutfitDto.Piece.OUTERWEAR, currentItem(outerwear.pager));
        if (under.on) addPiece(pieces, OutfitDto.Piece.UNDER, currentItem(under.pager));
        if (bag.on) addPiece(pieces, OutfitDto.Piece.BAG, currentItem(bag.pager));
        for (Layer spot : accessorySpots.values()) {
            if (spot.on) addPiece(pieces, OutfitDto.Piece.ACCESSORY, currentItem(spot.pager));
        }
        return pieces;
    }

    private static void addPiece(List<OutfitDto.Piece> pieces, String slot, @Nullable ItemDto item) {
        if (item != null) pieces.add(new OutfitDto.Piece(slot, item));
    }

    /**
     * Draws the outfit as it stands on the canvas into a picture with a see-through background: the
     * slots and the layers over them, and nothing else of the screen. Only the piece each pager is on
     * belongs to the outfit, so the pieces peeking in from the sides and the placeholders of empty
     * slots are hidden while it is drawn.
     */
    private Bitmap drawOutfit() {
        View slots = findViewById(R.id.canvas_slots);
        View stage = findViewById(R.id.canvas_layers);
        List<ViewPager2> pagers = new ArrayList<>(List.of(topPager, bottomPager, shoesPager));
        for (Layer layer : layers) pagers.add(layer.pager);
        Map<View, Float> hidden = new HashMap<>();
        for (ViewPager2 pager : pagers) {
            RecyclerView pages = (RecyclerView) pager.getChildAt(0);
            RecyclerView.ViewHolder current = pages.findViewHolderForAdapterPosition(pager.getCurrentItem());
            for (int i = 0; i < pages.getChildCount(); i++) {
                View page = pages.getChildAt(i);
                if (current != null && page == current.itemView) continue;
                hidden.put(page, page.getAlpha());
                page.setAlpha(0f);
            }
        }
        // An empty slot's "Add shoes…" isn't part of the outfit either.
        for (View placeholder : placeholders.values()) {
            hidden.put(placeholder, placeholder.getAlpha());
            placeholder.setAlpha(0f);
        }
        Bitmap picture = Bitmap.createBitmap(slots.getWidth(), slots.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(picture);
        slots.draw(canvas);
        stage.draw(canvas);
        hidden.forEach(View::setAlpha);
        return picture;
    }

    /** Shows "13° · cool" for the user's city. If the weather can't be loaded the chip just stays hidden. */
    private void loadWeather() {
        api.weather().enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable WeatherDto now) {
                if (now == null) return;
                weather = now;
                weatherChip.setText(getString(R.string.canvas_weather, now.temperature, now.feel));
                weatherChip.setContentDescription(now.city + ": " + now.temperature + "°, " + now.sky);
                weatherChip.setVisibility(View.VISIBLE);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                // No weather is fine: the canvas and the stylist work without it.
            }
        });
    }

    // ----- Loading the wardrobe into the slots and layers -----

    private void loadWardrobe() {
        api.items(userId).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable List<ItemDto> items) {
                findViewById(R.id.canvas_loading).setVisibility(View.GONE);
                if (items == null) return;
                boolean firstLoad = !wardrobeLoaded;
                wardrobeLoaded = true;
                wardrobe = items;
                if (!showWardrobe()) return; // nothing to style yet
                // A saved outfit being changed is put on below; a reloaded wardrobe keeps the outfit.
                if (firstLoad && editing == null) startOnRandomPieces();
                askAi.setEnabled(true);
                if (addedItemId >= 0) {
                    for (ItemDto item : items) {
                        if (item.id != addedItemId) continue;
                        if (!offered(item)) clearFilter(); // the new piece shouldn't be hidden by the filter
                        showAdded(item);
                    }
                    addedItemId = -1;
                }
                if (editing != null && !editingShown) {
                    editingShown = true;
                    // The pages are laid out on the next frame; only then do the slots know their pieces' places.
                    topPager.post(() -> wearSaved(editing));
                }
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.canvas_loading).setVisibility(View.GONE);
                Toast.makeText(OutfitCanvasActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Fills the slots and layers from the wardrobe, each with the pieces that pass the filter; a slot
     * keeps the piece it shows while that piece still passes. Returns false, showing "Nothing to style
     * yet", when the closet has no tops, bottoms, shoes or outerwear at all.
     */
    private boolean showWardrobe() {
        List<ItemDto> tops = new ArrayList<>();
        List<ItemDto> bottoms = new ArrayList<>();
        List<ItemDto> shoes = new ArrayList<>();
        List<ItemDto> bags = new ArrayList<>();
        List<ItemDto> unders = new ArrayList<>();
        List<ItemDto> outers = new ArrayList<>();
        List<ItemDto> worn = new ArrayList<>();
        for (ItemDto item : wardrobe) {
            if (item.category == null) continue; // not placed in a slot until the user chooses a type
            switch (item.category) {
                case Categories.TOP -> tops.add(item);
                case Categories.BOTTOM -> bottoms.add(item);
                case Categories.SHOES -> shoes.add(item);
                case Categories.OUTERWEAR -> outers.add(item);
                case Categories.BAG -> bags.add(item);
                case Categories.ACCESSORY -> {
                    if (AccessorySpot.of(item.type) != null) worn.add(item);
                }
                default -> { }
            }
            if (Categories.OUTERWEAR.equals(item.alsoCategory)) outers.add(item);
            if (Categories.TOP.equals(item.category) && item.underLayer) unders.add(item);
        }
        boolean nothingToStyle = tops.isEmpty() && bottoms.isEmpty() && shoes.isEmpty() && outers.isEmpty();
        findViewById(R.id.canvas_empty).setVisibility(nothingToStyle ? View.VISIBLE : View.GONE);
        if (nothingToStyle) return false;

        topPieces = narrowed(topPager, tops);
        bottomPieces = narrowed(bottomPager, bottoms);
        List<ItemDto> shoesShown = narrowed(shoesPager, shoes);
        replace(underPieces, narrowed(under.pager, unders));
        replace(outerwearPieces, narrowed(outerwear.pager, outers));
        replace(accessories, filter.narrow(worn));
        accessoriesFilteredOut = accessories.isEmpty() && !worn.isEmpty();
        offerTops();
        offerBottoms();
        setUpPager(shoesPager, shoesShown, SHOES_SCALE, null, null);
        offerLayersExcept(currentItem(topPager));
        setUpPager(bag.pager, narrowed(bag.pager, bags), 1f, bag, null);
        // A layer the filter left with nothing comes off.
        for (Layer layer : List.of(under, outerwear, bag)) {
            if (layer.on && currentItem(layer.pager) == null) takeOff(layer);
        }
        showAccessories(List.of());
        return true;
    }

    /** These pieces as far as they pass the filter; remembers when the filter hides every one of them. */
    private List<ItemDto> narrowed(ViewPager2 pager, List<ItemDto> pieces) {
        List<ItemDto> passing = filter.narrow(pieces);
        if (passing.isEmpty() && !pieces.isEmpty()) filteredOut.add(pager);
        else filteredOut.remove(pager);
        return passing;
    }

    private static void replace(List<ItemDto> list, List<ItemDto> pieces) {
        list.clear();
        list.addAll(pieces);
    }

    // ----- The filter -----

    /** The filter sheet: the colors and types the closet has, with the ones chosen now ticked. */
    private void openFilter() {
        if (!wardrobeLoaded) return; // the choices are made from the clothes, and they aren't here yet
        List<ItemDto> styled = new ArrayList<>();
        for (ItemDto item : wardrobe) {
            if (item.category != null) styled.add(item);
        }
        FilterSheet.newInstance(KEY_FILTER, filter.groupsForCanvas(this, styled))
                .show(getSupportFragmentManager(), KEY_FILTER);
    }

    private void clearFilter() {
        filter.clear();
        filterChanged();
    }

    /** The carousels show what the filter lets through now, and the dot on the filter button says if it is on. */
    private void filterChanged() {
        findViewById(R.id.canvas_filter_badge).setVisibility(filter.count() > 0 ? View.VISIBLE : View.GONE);
        if (wardrobeLoaded) showWardrobe();
    }

    /** Whether a carousel offers this piece right now: the filter may hide it. */
    private boolean offered(ItemDto item) {
        for (List<ItemDto> pieces : List.of(topPieces, bottomPieces, piecesOf(shoesPager), underPieces,
                outerwearPieces, piecesOf(bag.pager), accessories)) {
            for (ItemDto piece : pieces) {
                if (piece.id == item.id) return true;
            }
        }
        return false;
    }

    private static List<ItemDto> piecesOf(ViewPager2 pager) {
        return pager.getAdapter() instanceof SlotPagerAdapter adapter ? adapter.getItems() : List.of();
    }

    /**
     * One slot or layer = one pager. In a base slot (layer == null) and in a wide layer, side padding
     * leaves room for the previous and next piece, faded and smaller; a layer shows them only in focus.
     * A pager that already shows pieces (the wardrobe was reloaded) stays on the piece it was showing.
     * A base slot with no pieces gives way to its placeholder.
     */
    private void setUpPager(ViewPager2 pager, List<ItemDto> items, float slotScale, @Nullable Layer layer,
            @Nullable SlotPagerAdapter.PageStyle pageStyle) {
        setUpPager(pager, items, slotScale, layer, pageStyle, false);
    }

    /**
     * @param emptyFirst start with an empty page (the bottoms under a dress): the slot then never needs
     *                   a placeholder
     */
    private void setUpPager(ViewPager2 pager, List<ItemDto> items, float slotScale, @Nullable Layer layer,
            @Nullable SlotPagerAdapter.PageStyle pageStyle, boolean emptyFirst) {
        SlotPlaceholderView placeholder = placeholders.get(pager);
        if (placeholder != null) {
            boolean missing = items.isEmpty() && !emptyFirst;
            placeholder.showNoMatch(filteredOut.contains(pager));
            // Hidden, the empty pager can't take the taps meant for the placeholder.
            pager.setVisibility(missing ? View.INVISIBLE : View.VISIBLE);
            placeholder.setVisibility(missing ? View.VISIBLE : View.GONE);
        }
        // The side padding must be in place before the pages are laid out, or the first piece
        // would sit against the left edge instead of the center.
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        boolean wide = layer == null || layer.wide;
        int side = wide ? Math.round(getResources().getDisplayMetrics().widthPixels * sidePeek) : 0;
        pages.setPadding(side, 0, side, 0);
        pages.setClipToPadding(!wide);
        pages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setOffscreenPageLimit(1);
        ItemDto shown = currentItem(pager);
        SlotPagerAdapter adapter = new SlotPagerAdapter(items, pageStyle, emptyFirst);
        pager.setAdapter(adapter);
        int keep = shown == null ? -1 : adapter.indexOf(shown.id);
        if (keep > 0) {
            pager.setCurrentItem(keep, false);
            pager.post(pager::requestTransform); // an instant move doesn't re-run the page transformer
        }
        pager.setPageTransformer((page, position) -> {
            float centered = 1 - Math.min(1f, Math.abs(position)); // 1 in the middle, 0 one page away
            // A piece under the top takes its size from the top, not from its own "Edit photo" size.
            float itemScale = layer != under && page.getTag() instanceof Float f ? f : 1f;
            float neighborAlpha = layer == null || focused.contains(layer) ? NEIGHBOR_ALPHA : 0f;
            page.setAlpha(neighborAlpha + (1 - neighborAlpha) * centered);
            // Off to the side there is no top in front of an under piece, so it is shown whole there.
            if (layer == under && page instanceof PieceImageView piece) piece.setWhole(centered < 0.5f);
            float scale = (NEIGHBOR_SCALE + (1 - NEIGHBOR_SCALE) * centered) * itemScale * slotScale;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });
    }

    /**
     * A new look starts on a random piece in the top, the bottom and the shoes, so the canvas suggests a
     * different outfit every time it opens. The layers stay off.
     */
    private void startOnRandomPieces() {
        moveToRandom(topPager);
        offerBottoms();
        if (!isDress(currentItem(topPager))) moveToRandom(bottomPager); // a dress starts with no bottoms
        moveToRandom(shoesPager);
    }

    private void moveToRandom(ViewPager2 slot) {
        int count = slot.getAdapter() == null ? 0 : slot.getAdapter().getItemCount();
        if (count < 2) return;
        slot.setCurrentItem(random.nextInt(count), false);
        slot.post(slot::requestTransform); // an instant move doesn't re-run the page transformer
    }

    /**
     * Fills the top slot with every top except the ones that are on as the under layer or the outerwear
     * right now: a piece already worn there isn't offered again as the top. The top that is showing
     * stays where it is. Does nothing when the slot already offers exactly these pieces.
     */
    private void offerTops() {
        ItemDto wornUnder = under.on ? currentItem(under.pager) : null;
        ItemDto wornOver = outerwear.on ? currentItem(outerwear.pager) : null;
        List<ItemDto> choices = new ArrayList<>();
        for (ItemDto piece : topPieces) {
            boolean worn = (wornUnder != null && wornUnder.id == piece.id)
                    || (wornOver != null && wornOver.id == piece.id);
            if (!worn) choices.add(piece);
        }
        if (topPager.getAdapter() instanceof SlotPagerAdapter adapter && sameIds(adapter.getItems(), choices)) {
            return;
        }
        // The top's image tells the layers around it where it is, whenever it changes.
        setUpPager(topPager, choices, 1f, null, (image, item) -> {
            PieceImageView piece = (PieceImageView) image;
            // A dress reaches from the top of its slot to the bottom of the outfit's body, like a long coat.
            if (isDress(item)) piece.hangLong(1f / topShare);
            else piece.fitInside();
            piece.setOpening(item.opening);
            piece.setOnImageChanged(this::alignToTop);
        });
        offerBottoms(); // the top showing may have changed, and a dress changes the bottoms
        // The new pages can only be asked where the top is once they are laid out.
        topPager.post(this::alignToTop);
    }

    /**
     * The bottom slot follows the top. A dress takes the place of the bottoms, so with a dress on, the
     * slot gets an empty first page and moves to it; the user can still swipe on to leggings or jeans to
     * wear under the dress. With any other top the slot shows a bottom again, the one it had before the
     * dress. Does nothing when the slot already offers the right pages.
     */
    private void offerBottoms() {
        boolean dress = isDress(currentItem(topPager));
        boolean wasDress = bottomPager.getAdapter() instanceof SlotPagerAdapter adapter && adapter.hasEmptyFirst();
        if (bottomPager.getAdapter() instanceof SlotPagerAdapter adapter && dress == wasDress
                && sameIds(adapter.getItems(), bottomPieces)) {
            return;
        }
        ItemDto shown = currentItem(bottomPager);
        setUpPager(bottomPager, bottomPieces, 1f, null, null, dress);
        if (dress && !wasDress) {
            bottomBeforeDress = shown;
            bottomPager.setCurrentItem(0, false);
            bottomPager.post(bottomPager::requestTransform);
        } else if (!dress && wasDress && shown == null) {
            moveTo(bottomPager, bottomBeforeDress, false);
        }
        layBottom();
    }

    /**
     * The bottom is drawn over the top's hem (tucked in), except under a dress and when a piece is worn
     * under the top: then the top hangs over it.
     */
    private void layBottom() {
        boolean covered = under.on || isDress(currentItem(topPager));
        bottomPager.setTranslationZ(covered ? 0f : Z_TUCKED_BOTTOM);
    }

    private static boolean isDress(@Nullable ItemDto item) {
        return item != null && DRESS.equals(item.type);
    }

    /**
     * A piece can't be worn under or over itself: the under layer and the outerwear offer everything
     * they have except the piece that is the top (a T-shirt under a sweater, but not under itself; a
     * cardigan as the top or as the outerwear, not both).
     */
    private void offerLayersExcept(@Nullable ItemDto top) {
        offerExcept(under, underPieces, top, 1f, (image, item) -> placeUnder((PieceImageView) image));
        offerExcept(outerwear, outerwearPieces, top, OUTERWEAR_SCALE, this::styleOuterwearPage);
    }

    /**
     * Fills a layer with its pieces except the one that is the top. If that piece was the one on screen,
     * the layer comes off. Does nothing when the layer already offers exactly these pieces.
     */
    private void offerExcept(Layer layer, List<ItemDto> pieces, @Nullable ItemDto top, float scale,
            SlotPagerAdapter.PageStyle pageStyle) {
        List<ItemDto> choices = new ArrayList<>();
        for (ItemDto piece : pieces) {
            if (top == null || piece.id != top.id) choices.add(piece);
        }
        if (layer.pager.getAdapter() instanceof SlotPagerAdapter adapter && sameIds(adapter.getItems(), choices)) {
            return;
        }
        ItemDto worn = layer.on ? currentItem(layer.pager) : null;
        if (worn != null && top != null && worn.id == top.id) takeOff(layer);
        setUpPager(layer.pager, choices, scale, layer, pageStyle);
    }

    private static boolean sameIds(List<ItemDto> a, List<ItemDto> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).id != b.get(i).id) return false;
        }
        return true;
    }

    /**
     * Outerwear shares one frame with the top and the bottom. It hangs from the shoulder line of the top
     * and is as wide as the top, shoulder to shoulder (the page then makes it a touch larger); how far
     * down it reaches follows from its photo, a short jacket to the hips, a coat over the bottom, but
     * never past the end of the bottom. Only the left half is drawn: an open jacket over the left side
     * of the outfit, the clothes underneath still visible on the right.
     */
    private void styleOuterwearPage(ImageView image, ItemDto item) {
        // Outerwear is always worn over something, so an open jacket never shows the inside of its back.
        ((PieceImageView) image).setOpening(item.opening);
        ((PieceImageView) image).leaveOpeningOut(true);
        whenLaidOut(image, () -> {
            int width = image.getWidth();
            int height = image.getHeight();
            image.setPivotX(width / 2f);
            image.setClipBounds(new Rect(0, 0, width / 2, height));
            hang((PieceImageView) image, shoulderLine(), topWidth());
        });
    }

    /**
     * The rectangle the image of the top that is showing covers, inside the body frame (the top's slot
     * starts at the same corner as the frame). Null while there is no top, or its image hasn't arrived.
     */
    @Nullable
    private RectF topBounds() {
        PieceImageView piece = topView();
        return piece == null ? null : piece.imageBounds(piece.getTag() instanceof Float scale ? scale : 1f);
    }

    /** The view of the top that is showing, or null while its page doesn't exist yet. */
    @Nullable
    private PieceImageView topView() {
        RecyclerView pages = (RecyclerView) topPager.getChildAt(0);
        RecyclerView.ViewHolder page = pages.findViewHolderForAdapterPosition(topPager.getCurrentItem());
        return page != null && page.itemView instanceof PieceImageView piece ? piece : null;
    }

    /** How far below the top of its slot the top starts: the line outerwear hangs from. */
    private float shoulderLine() {
        RectF top = topBounds();
        return top == null ? 0f : Math.max(0f, top.top);
    }

    /** How wide the top that is showing is drawn: the width outerwear takes. 0 while there is no top. */
    private float topWidth() {
        RectF top = topBounds();
        return top == null ? 0f : top.width();
    }

    /** Redraws the layers around the top after it changed (another top, or its image just arrived). */
    private void alignToTop() {
        float shoulderLine = shoulderLine();
        float topWidth = topWidth();
        RecyclerView jackets = (RecyclerView) outerwear.pager.getChildAt(0);
        for (int i = 0; i < jackets.getChildCount(); i++) {
            if (jackets.getChildAt(i) instanceof PieceImageView piece) hang(piece, shoulderLine, topWidth);
        }
        RecyclerView underPages = (RecyclerView) under.pager.getChildAt(0);
        for (int i = 0; i < underPages.getChildCount(); i++) {
            if (underPages.getChildAt(i) instanceof PieceImageView piece) placeUnder(piece);
        }
        // An open-front top (an open cardigan) shows the inside of its back in its opening. With a piece
        // worn under it, that piece is what shows there; with nothing under it the top stays as it is.
        PieceImageView top = topView();
        RecyclerView tops = (RecyclerView) topPager.getChildAt(0);
        for (int i = 0; i < tops.getChildCount(); i++) {
            if (tops.getChildAt(i) instanceof PieceImageView piece) piece.leaveOpeningOut(under.on && piece == top);
        }
    }

    private static void hang(PieceImageView piece, float shoulderLine, float width) {
        piece.hangFrom(shoulderLine, width);
        piece.setPivotY(shoulderLine); // a resized jacket grows down from the shoulders, not up over them
    }

    /**
     * A piece worn under the top is drawn behind it: collar above the top's neckline, hem below its hem,
     * and whole where the top is open down the front. While the top's image isn't there (it was just
     * swapped), the piece keeps the place it had.
     */
    private void placeUnder(PieceImageView piece) {
        PieceImageView top = topView();
        RectF bounds = topBounds();
        if (top != null && bounds != null) piece.drawBehind(bounds, top.isOpenFront() ? top.outline(bounds) : null);
    }

    /** Gives the bag and every accessory spot its rectangle on the canvas. */
    private void placeLayers() {
        View stage = findViewById(R.id.canvas_layers);
        View slots = findViewById(R.id.canvas_slots);
        int width = stage.getWidth();
        int outfitTop = slots.getPaddingTop();
        int outfitHeight = stage.getHeight() - outfitTop - slots.getPaddingBottom();
        for (AccessorySpot spot : AccessorySpot.values()) {
            place(accessorySpots.get(spot).pager, spot.left, spot.right, spot.top, spot.bottom,
                    width, outfitTop, outfitHeight);
        }
        place(bag.pager, BAG_LEFT, BAG_RIGHT, BAG_TOP, BAG_BOTTOM, width, outfitTop, outfitHeight);
    }

    private static void place(View pager, float left, float right, float top, float bottom,
            int width, int outfitTop, int outfitHeight) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) pager.getLayoutParams();
        int y = Math.max(0, outfitTop + Math.round(top * outfitHeight)); // the head starts above the outfit
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMarginStart(Math.round(left * width));
        params.topMargin = y;
        params.width = Math.round((right - left) * width);
        params.height = outfitTop + Math.round(bottom * outfitHeight) - y;
        pager.setLayoutParams(params);
    }

    /** Runs the action now if the view already has its size, otherwise right after its next layout. */
    private static void whenLaidOut(View view, Runnable action) {
        if (view.isLaidOut() && !view.isLayoutRequested()) {
            action.run();
            return;
        }
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                v.removeOnLayoutChangeListener(this);
                action.run();
            }
        });
    }

    // ----- Putting layers on and taking them off -----

    /** Shows the layer; with an item, on that item (otherwise where the pager already is). */
    private void putOn(Layer layer, @Nullable ItemDto item) {
        layer.on = true;
        layer.pager.setVisibility(View.VISIBLE);
        if (layer == under) {
            layBottom(); // the under layer's hem hangs over the bottom
            alignToTop();
        }
        if (item != null && layer.pager.getAdapter() instanceof SlotPagerAdapter adapter) {
            int index = adapter.indexOf(item.id);
            if (index >= 0) layer.pager.setCurrentItem(index, false);
        }
        // An instant move doesn't re-run the page transformer, so the pages would keep the fading and
        // size of the place they were in before. Redo it once the pages are laid out.
        layer.pager.post(layer.pager::requestTransform);
        if (layer == under || layer == outerwear) offerTops();
    }

    private void takeOff(Layer layer) {
        layer.on = false;
        layer.pager.setVisibility(View.GONE);
        if (layer == under) {
            layBottom();
            alignToTop(); // an open-front top gets the inside of its back again
        }
        if (layer == under || layer == outerwear) offerTops(); // its piece can be the top again
    }

    /**
     * Puts the layer on at this item, or takes it off when there is no item, or when the layer doesn't
     * offer it (it would show whatever piece it was on instead).
     */
    private void show(Layer layer, @Nullable ItemDto item) {
        boolean offered = item != null && layer.pager.getAdapter() instanceof SlotPagerAdapter adapter
                && adapter.indexOf(item.id) >= 0;
        if (offered) putOn(layer, item);
        else takeOff(layer);
    }

    /** Outerwear / Bag: put the layer on if it isn't, then let the user swipe through it. */
    private void chooseLayer(Layer layer, @StringRes int hint, @StringRes int missingTitle,
            @StringRes int missingBody, @StringRes int noMatch) {
        if (!wardrobeLoaded) return;
        if (filteredOut.contains(layer.pager)) {
            Toast.makeText(this, noMatch, Toast.LENGTH_LONG).show();
            return;
        }
        if (currentItem(layer.pager) == null) {
            askToAdd(missingTitle, missingBody, layer);
            return;
        }
        if (!layer.on) putOn(layer, null);
        setFocus(List.of(layer), hint);
    }

    /** Accessories: first choose which kinds to wear, from the kinds the closet has. */
    private void chooseAccessoryTypes() {
        if (!wardrobeLoaded) return;
        ArrayList<OptionsSheet.Option> options = new ArrayList<>();
        Set<String> listed = new LinkedHashSet<>();
        for (ItemDto item : accessories) {
            if (listed.add(item.type)) options.add(new OptionsSheet.Option(item.type, item.typeLabel, null, 0));
        }
        if (accessoriesFilteredOut) {
            Toast.makeText(this, R.string.canvas_no_match_accessories, Toast.LENGTH_LONG).show();
            return;
        }
        if (options.isEmpty()) {
            askToAdd(R.string.missing_accessories_title, R.string.missing_accessories_body, null);
            return;
        }
        OptionsSheet.newInstance(KEY_ACCESSORIES, getString(R.string.canvas_accessories_title),
                        OptionsSheet.Mode.CHIPS_MULTI, options, new ArrayList<>(wornTypes), null)
                .show(getSupportFragmentManager(), KEY_ACCESSORIES);
    }

    /** Wears exactly these accessory types and, if there are any, lets the user swipe through them. */
    private void wear(@Nullable List<String> types) {
        wornTypes.clear();
        if (types != null) wornTypes.addAll(types);
        showAccessories(List.of());
        if (!wornTypes.isEmpty()) setFocus(accessorySpots.values(), R.string.canvas_focus_accessories);
    }

    /**
     * "You don't have one yet": the closet has nothing for what the user (or the weather) asked for.
     * They can go and add a piece, and come straight back to the outfit with it, or skip it.
     *
     * @param wanted the layer the piece is wanted for, when a layer tile was tapped
     */
    private void askToAdd(@StringRes int title, @StringRes int body, @Nullable Layer wanted) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton(R.string.missing_skip, null)
                .setPositiveButton(R.string.missing_add, (dialog, which) -> {
                    wantedLayer = wanted;
                    addPiece.launch(AddItemActivity.forResult(this));
                })
                .show();
    }

    /**
     * The user is back with a new piece, and the wardrobe has been loaded again. Says what it was
     * recognized as and puts it on, ready to swipe: a bag, an accessory or outerwear goes to its layer;
     * a thin top goes under the top if that is what was being added. Anything else just joins its slot.
     */
    private void showAdded(ItemDto item) {
        String label = item.typeLabel != null ? item.typeLabel : getString(Categories.label(item.category));
        Toast.makeText(this, getString(R.string.canvas_added, label), Toast.LENGTH_LONG).show();
        ItemDto top = currentItem(topPager);
        boolean isTheTop = top != null && top.id == item.id;
        if (Categories.BAG.equals(item.category)) {
            putOn(bag, item);
            setFocus(List.of(bag), R.string.canvas_focus_bag);
        } else if (Categories.ACCESSORY.equals(item.category) && AccessorySpot.of(item.type) != null) {
            wornTypes.add(item.type);
            showAccessories(List.of(item));
            setFocus(accessorySpots.values(), R.string.canvas_focus_accessories);
        } else if (wantedLayer == under && item.underLayer && !isTheTop) {
            putOn(under, item);
            setFocus(List.of(under), R.string.canvas_focus_under);
        } else if (!isTheTop && (Categories.OUTERWEAR.equals(item.category)
                || (wantedLayer == outerwear && Categories.OUTERWEAR.equals(item.alsoCategory)))) {
            putOn(outerwear, item);
            setFocus(List.of(outerwear), R.string.canvas_focus_outerwear);
        }
        wantedLayer = null;
    }

    /**
     * Fills every accessory spot with the owned pieces of the worn types that belong there, and hides
     * the spots left empty. A spot shows the piece from "wanted" if one is meant for it, otherwise it
     * keeps the piece it was already showing.
     */
    private void showAccessories(List<ItemDto> wanted) {
        for (AccessorySpot spot : AccessorySpot.values()) {
            Layer layer = accessorySpots.get(spot);
            List<ItemDto> items = new ArrayList<>();
            for (ItemDto item : accessories) {
                if (wornTypes.contains(item.type) && spot.types.contains(item.type)) items.add(item);
            }
            if (items.isEmpty()) {
                takeOff(layer);
                continue;
            }
            ItemDto target = layer.on ? currentItem(layer.pager) : null;
            for (ItemDto item : wanted) {
                if (spot.types.contains(item.type)) target = item;
            }
            boolean sameItems = layer.pager.getAdapter() instanceof SlotPagerAdapter adapter
                    && adapter.getItems().equals(items);
            if (!sameItems) setUpPager(layer.pager, items, 1f, layer, null);
            putOn(layer, target);
        }
    }

    // ----- Focus: choosing layers while the rest of the outfit is locked -----

    /** Locks everything except these layers; the hint says what to do with them. */
    private void setFocus(Collection<Layer> chosen, @StringRes int hint) {
        focused.clear();
        focused.addAll(chosen);
        ((TextView) findViewById(R.id.canvas_focus_hint)).setText(hint);
        applyFocus();
    }

    private void leaveFocus() {
        focused.clear();
        applyFocus();
    }

    private void applyFocus() {
        boolean locked = !focused.isEmpty();
        for (ViewPager2 slot : List.of(topPager, bottomPager, shoesPager)) {
            slot.setUserInputEnabled(!locked);
            // The piece under the top only shows around the top, so the top stays as it is for that.
            boolean faded = locked && !(slot == topPager && focused.contains(under));
            slot.setAlpha(faded ? LOCKED_ALPHA : 1f);
            View placeholder = placeholders.get(slot);
            placeholder.setEnabled(!locked);
            placeholder.setAlpha(locked ? LOCKED_ALPHA : 1f);
        }
        for (Layer layer : layers) {
            boolean inFocus = focused.contains(layer);
            layer.pager.setUserInputEnabled(inFocus);
            layer.pager.setAlpha(locked && !inFocus ? LOCKED_ALPHA : 1f);
            layer.pager.requestTransform(); // neighbors appear in focus and disappear after it
        }
        findViewById(R.id.canvas_tools).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.canvas_focus_bar).setVisibility(locked ? View.VISIBLE : View.GONE);
        findViewById(R.id.canvas_focus_hint).setVisibility(locked ? View.VISIBLE : View.GONE);
        findViewById(R.id.canvas_ask_ai_holder).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.canvas_next).setEnabled(!locked);
        findViewById(R.id.canvas_filter).setEnabled(!locked);
        weatherChip.setEnabled(!locked);
        leaveFocusOnBack.setEnabled(locked);
    }

    /** "Remove" in the focus bar: the layers being chosen leave the outfit. */
    private void removeFocusedLayers() {
        for (Layer layer : focused) {
            if (layer.spot != null) wornTypes.removeAll(layer.spot.types);
            else takeOff(layer);
        }
        showAccessories(List.of());
        leaveFocus();
    }

    // ----- The weather tip -----

    /**
     * Opened by the weather chip or by Next: offers what the weather calls for, the closet has and the
     * outfit lacks — a jacket, beanie, scarf and gloves when it is cool or cold, a cap, hat or headscarf
     * in the heat. With nothing to offer, Next simply goes on, and the chip says why: either the outfit
     * is fine, or the closet has nothing suitable (then the user may go and add a piece).
     */
    private void offerWeatherTip(boolean fromNext) {
        if (!wardrobeLoaded) {
            if (fromNext) goNext();
            return;
        }
        boolean cold = isCold();
        List<String> types = cold ? COLD_TYPES : isHot() ? HOT_TYPES : List.of();
        ArrayList<WeatherTipSheet.Piece> pieces = new ArrayList<>();
        boolean lacking = false; // called for, not worn, and not in the closet either
        if (cold && !outerwear.on) {
            ItemDto jacket = currentItem(outerwear.pager);
            if (jacket == null) lacking = true;
            else pieces.add(tipPiece(TIP_OUTERWEAR, getString(R.string.weather_tip_jacket), jacket));
        }
        for (String type : types) {
            if (accessorySpots.get(AccessorySpot.of(type)).on) continue; // that spot is already taken
            ItemDto owned = null;
            for (ItemDto item : accessories) {
                if (type.equals(item.type)) {
                    owned = item;
                    break;
                }
            }
            if (owned == null) lacking = true;
            else pieces.add(tipPiece(type, owned.typeLabel, owned));
        }
        if (pieces.isEmpty()) {
            if (fromNext) {
                goNext();
            } else if (lacking) {
                askToAdd(cold ? R.string.missing_cold_title : R.string.missing_hot_title,
                        cold ? R.string.missing_cold_body : R.string.missing_hot_body, null);
            } else {
                Toast.makeText(this, R.string.weather_tip_fine, Toast.LENGTH_LONG).show();
            }
            return;
        }
        tipFromNext = fromNext;
        weatherTipOffered = true;
        WeatherTipSheet.newInstance(weatherChip.getText().toString(), cold, pieces)
                .show(getSupportFragmentManager(), WeatherTipSheet.REQUEST_KEY);
    }

    /** A card for the tip: the kind of piece and the user's own one, named like "Camel coat". */
    private WeatherTipSheet.Piece tipPiece(String key, String kind, ItemDto item) {
        return new WeatherTipSheet.Piece(key, kind, ItemNames.of(this, item), item.imageUrl);
    }

    /** Puts on what the user ticked in the weather tip, then lets them swipe through those layers. */
    private void addFromWeatherTip(@Nullable List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        List<Layer> swappable = new ArrayList<>(accessorySpots.values());
        for (String key : keys) {
            if (TIP_OUTERWEAR.equals(key)) {
                putOn(outerwear, null);
                swappable.add(outerwear);
            } else {
                wornTypes.add(key);
            }
        }
        showAccessories(List.of());
        setFocus(swappable, R.string.canvas_focus_layers);
    }

    /** Cool, cold or freezing: time for a jacket and the warm accessories. */
    private boolean isCold() {
        return weather != null && weather.feel != null
                && List.of("freezing", "cold", "cool").contains(weather.feel);
    }

    /** Hot, or warm under a clear sky: time for something on the head. */
    private boolean isHot() {
        if (weather == null || weather.feel == null) return false;
        boolean sunny = "clear".equals(weather.sky) || "partly cloudy".equals(weather.sky);
        return "hot".equals(weather.feel) || ("warm".equals(weather.feel) && sunny);
    }

    // ----- The AI stylist -----

    private void generate(String hint) {
        setStyling(true);
        Map<String, Object> body = new HashMap<>();
        if (!hint.isEmpty()) body.put("hint", hint);
        if (!conversation.isEmpty()) body.put("history", new ArrayList<>(conversation));
        List<ItemDto> worn = new ArrayList<>();
        for (Layer spot : accessorySpots.values()) {
            if (spot.on) worn.add(currentItem(spot.pager));
        }
        body.put("current", lookIds(currentItem(topPager), currentItem(bottomPager), currentItem(shoesPager),
                outerwear.on ? currentItem(outerwear.pager) : null, under.on ? currentItem(under.pager) : null,
                bag.on ? currentItem(bag.pager) : null, worn));
        api.generateOutfit(userId, body).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable GenerateResult look) {
                setStyling(false);
                if (look == null) return;
                makeRoomFor(look);
                wearLook(look.top, look.bottom, look.shoes, look.outerwear, look.under, look.bag,
                        look.accessories == null ? List.of() : look.accessories, true);
                remember(hint, look);
                if (look.message != null) {
                    Toast.makeText(OutfitCanvasActivity.this, look.message, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setStyling(false);
                Toast.makeText(OutfitCanvasActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** The stylist chooses from the whole closet: if its look has a piece the filter hides, the filter goes. */
    private void makeRoomFor(GenerateResult look) {
        if (filter.count() == 0) return;
        List<ItemDto> pieces = new ArrayList<>(look.accessories == null ? List.of() : look.accessories);
        for (ItemDto piece : new ItemDto[] {look.top, look.bottom, look.shoes, look.outerwear, look.under, look.bag}) {
            if (piece != null) pieces.add(piece);
        }
        for (ItemDto piece : pieces) {
            if (!offered(piece)) {
                clearFilter();
                Toast.makeText(this, R.string.canvas_filter_cleared, Toast.LENGTH_LONG).show();
                return;
            }
        }
    }

    // ----- Putting a whole look on -----

    /**
     * Moves every slot to its piece and puts exactly the given layers on (null = that layer off). Used
     * for the stylist's look and for a saved outfit being edited.
     *
     * @param animate let the slots slide to their pieces (the stylist), or jump there (an outfit to edit)
     */
    private void wearLook(@Nullable ItemDto top, @Nullable ItemDto bottom, @Nullable ItemDto shoes,
            @Nullable ItemDto outer, @Nullable ItemDto underPiece, @Nullable ItemDto bagPiece,
            List<ItemDto> accessoryPieces, boolean animate) {
        // A piece worn as a layer isn't offered as the top, and the new top may be just that piece:
        // the layers come off first and go back on once the top is chosen.
        takeOff(under);
        takeOff(outerwear);
        moveTo(topPager, top, animate);
        offerBottoms();
        if (bottom == null && isDress(currentItem(topPager))) {
            bottomPager.setCurrentItem(0, animate); // the empty page: nothing under the dress
            if (!animate) bottomPager.post(bottomPager::requestTransform);
        } else {
            moveTo(bottomPager, bottom, animate);
        }
        moveTo(shoesPager, shoes, animate);
        // The top may still be scrolling to its new piece, so tell the layers which one it will be.
        offerLayersExcept(top != null ? top : currentItem(topPager));
        show(under, underPiece);
        show(outerwear, outer);
        show(bag, bagPiece);
        wornTypes.clear();
        for (ItemDto item : accessoryPieces) wornTypes.add(item.type);
        showAccessories(accessoryPieces);
        if (!animate) topPager.post(this::alignToTop);
    }

    /**
     * Edit: puts the saved outfit on. Its pieces go back where they were; a piece the canvas can't put
     * there any more (its type changed, or two accessories want the same spot) is left out, and the
     * user is told which.
     */
    private void wearSaved(OutfitDto outfit) {
        Map<String, ItemDto> bySlot = new HashMap<>();
        List<ItemDto> accessoryPieces = new ArrayList<>();
        for (OutfitDto.Piece piece : outfit.pieces) {
            if (OutfitDto.Piece.ACCESSORY.equals(piece.slot)) accessoryPieces.add(piece.item);
            else bySlot.put(piece.slot, piece.item);
        }
        wearLook(bySlot.get(OutfitDto.Piece.TOP), bySlot.get(OutfitDto.Piece.BOTTOM),
                bySlot.get(OutfitDto.Piece.SHOES), bySlot.get(OutfitDto.Piece.OUTERWEAR),
                bySlot.get(OutfitDto.Piece.UNDER), bySlot.get(OutfitDto.Piece.BAG), accessoryPieces, false);

        Set<Long> worn = new HashSet<>();
        for (OutfitDto.Piece piece : wornPieces()) worn.add(piece.item.id);
        List<String> leftOut = new ArrayList<>();
        for (OutfitDto.Piece piece : outfit.pieces) {
            if (!worn.contains(piece.item.id)) leftOut.add(ItemNames.of(this, piece.item));
        }
        // The top, bottom and shoes always show a piece (but the bottoms under a dress). If the outfit had
        // none (it was removed from the wardrobe, or its type changed), the slot shows one from the closet,
        // which the user should know.
        List<String> filledIn = new ArrayList<>();
        for (String slot : List.of(OutfitDto.Piece.TOP, OutfitDto.Piece.BOTTOM, OutfitDto.Piece.SHOES)) {
            ItemDto shown = currentItem(slot.equals(OutfitDto.Piece.TOP) ? topPager
                    : slot.equals(OutfitDto.Piece.BOTTOM) ? bottomPager : shoesPager);
            if (!bySlot.containsKey(slot) && shown != null) filledIn.add(ItemNames.of(this, shown));
        }
        List<String> notes = new ArrayList<>();
        if (!leftOut.isEmpty()) {
            notes.add(getString(R.string.canvas_edit_left_out, String.join(getString(R.string.tags_joined_separator), leftOut)));
        }
        if (!filledIn.isEmpty()) {
            notes.add(getString(R.string.canvas_edit_filled_in, String.join(getString(R.string.tags_joined_separator), filledIn)));
        }
        if (!notes.isEmpty()) Toast.makeText(this, String.join("\n", notes), Toast.LENGTH_LONG).show();
    }

    // ----- The stylist's memory of this session -----

    private void remember(String hint, GenerateResult look) {
        Map<String, Object> turn = new HashMap<>();
        turn.put("hint", hint);
        turn.put("look", lookIds(look.top, look.bottom, look.shoes, look.outerwear, look.under, look.bag,
                look.accessories == null ? List.of() : look.accessories));
        conversation.add(turn);
        while (conversation.size() > MAX_REMEMBERED_REQUESTS) conversation.remove(0);
    }

    /** The most recent request text, or null before the first one (or after "Start over"). */
    @Nullable
    private String lastRequest() {
        if (conversation.isEmpty()) return null;
        String hint = (String) conversation.get(conversation.size() - 1).get("hint");
        return hint == null || hint.isEmpty() ? getString(R.string.ask_ai_surprise) : hint;
    }

    private static Map<String, Object> lookIds(@Nullable ItemDto top, @Nullable ItemDto bottom,
            @Nullable ItemDto shoes, @Nullable ItemDto outer, @Nullable ItemDto under, @Nullable ItemDto bag,
            List<ItemDto> accessories) {
        Map<String, Object> ids = new HashMap<>();
        ids.put("topId", top == null ? null : top.id);
        ids.put("bottomId", bottom == null ? null : bottom.id);
        ids.put("shoesId", shoes == null ? null : shoes.id);
        ids.put("outerwearId", outer == null ? null : outer.id);
        ids.put("underId", under == null ? null : under.id);
        ids.put("bagId", bag == null ? null : bag.id);
        List<Long> accessoryIds = new ArrayList<>();
        for (ItemDto item : accessories) {
            if (item != null) accessoryIds.add(item.id);
        }
        ids.put("accessoryIds", accessoryIds);
        return ids;
    }

    /** The piece a slot is showing right now, or null if the slot is empty. */
    @Nullable
    private ItemDto currentItem(ViewPager2 pager) {
        if (!(pager.getAdapter() instanceof SlotPagerAdapter adapter) || adapter.getItemCount() == 0) return null;
        return adapter.getItem(pager.getCurrentItem());
    }

    /** Moves a slot to this piece, if the slot has it; otherwise the slot stays where it is. */
    private void moveTo(ViewPager2 pager, @Nullable ItemDto item, boolean animate) {
        if (item == null || !(pager.getAdapter() instanceof SlotPagerAdapter adapter)) return;
        int index = adapter.indexOf(item.id);
        if (index < 0) return;
        pager.setCurrentItem(index, animate);
        // An instant move doesn't re-run the page transformer, so the pages would keep the size and
        // fading of the place they were in before.
        if (!animate) pager.post(pager::requestTransform);
    }

    /** While the stylist works, the Ask AI button can't be tapped and shows a spinner instead of its icon. */
    private void setStyling(boolean styling) {
        askAi.setEnabled(!styling);
        askAi.setImageResource(styling ? 0 : R.drawable.ic_sparkle);
        findViewById(R.id.canvas_styling).setVisibility(styling ? View.VISIBLE : View.GONE);
    }
}
