package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.WeatherDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;
import com.mixyn.app.ui.components.OptionsSheet;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Create outfit. The wardrobe is loaded once and split by category. The base of every outfit is three
 * slots (top, bottom, shoes), each a pager over its category. Over them lie optional layers, hidden
 * until they are put on: outerwear (only its left half is drawn, like an open jacket), a bag, and
 * accessories, each in its own spot on the outfit.
 *
 * A layer is chosen in "focus": tapping Outerwear, Bag or Accessories locks the rest of the outfit, so
 * swipes change only that layer, until the user taps Done. The round Ask AI button sends an optional
 * hint to the backend stylist, which may change the slots and all the layers at once.
 */
public class OutfitCanvasActivity extends BaseActivity {

    /** Share of the screen width left on each side of a piece, where its neighbors peek in. */
    private static final float SIDE_PEEK = 0.22f;
    private static final float NEIGHBOR_ALPHA = 0.25f;
    private static final float NEIGHBOR_SCALE = 0.8f;
    /** Outerwear is drawn a touch larger than the top it covers, like a jacket worn over a sweater. */
    private static final float OUTERWEAR_SCALE = 1.08f;
    /** Shoes are drawn smaller than the slot allows, so they sit under the trousers instead of on them. */
    private static final float SHOES_SCALE = 0.7f;
    /** How much of the outerwear's frame (top + bottom) the top takes; must match the layout weights. */
    private static final float TOP_SHARE = 36f / 78f;
    /** These reach down over the bottom; every other outerwear type covers only the top. */
    private static final List<String> LONG_OUTERWEAR = List.of("COAT", "TRENCH_COAT");
    /** Everything that can't be changed while one layer is being chosen is faded to this. */
    private static final float LOCKED_ALPHA = 0.45f;
    /** Where the bag hangs: by the hand, beside the bottom. Same units as AccessorySpot. */
    private static final float BAG_LEFT = 0.66f, BAG_RIGHT = 0.98f, BAG_TOP = 0.50f, BAG_BOTTOM = 0.76f;

    /** What the weather tip offers, by item type. */
    private static final List<String> COLD_TYPES = List.of("BEANIE", "SCARF", "GLOVES");
    private static final List<String> HOT_TYPES = List.of("CAP", "HAT", "HEADSCARF");

    private static final String KEY_ACCESSORIES = "canvas_accessories";

    /** Which layer the user is choosing right now; NONE = the normal canvas. */
    private enum Focus { NONE, OUTERWEAR, BAG, ACCESSORIES }

    /** One optional layer over the outfit: its pager and whether it is part of the outfit right now. */
    private static final class Layer {
        final ViewPager2 pager;
        final Focus group;
        /** True for layers as wide as the canvas, whose neighbors peek in at the sides while in focus. */
        final boolean wide;
        boolean on;

        Layer(ViewPager2 pager, Focus group, boolean wide) {
            this.pager = pager;
            this.group = group;
            this.wide = wide;
            pager.setUserInputEnabled(false); // swipes reach a layer only while it is in focus
        }
    }

    private MixynApi api;
    private long userId;
    private ViewPager2 topPager;
    private ViewPager2 bottomPager;
    private ViewPager2 shoesPager;
    private Layer outerwear;
    private Layer bag;
    private final Map<AccessorySpot, Layer> accessorySpots = new EnumMap<>(AccessorySpot.class);
    private final List<Layer> layers = new ArrayList<>();
    private ImageButton askAi;
    private TextView weatherChip;
    private Focus focus = Focus.NONE;
    private OnBackPressedCallback leaveFocusOnBack;

    /** Every accessory in the wardrobe, and the types the user chose to wear ("SCARF", "CAP"…). */
    private final List<ItemDto> accessories = new ArrayList<>();
    private final Set<String> wornTypes = new LinkedHashSet<>();

    @Nullable
    private WeatherDto weather;
    /** The types the open weather tip is offering, and whether it interrupted Next. */
    private List<String> tipTypes = List.of();
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outfit_canvas);
        api = ApiClient.get(this);
        userId = new SessionStore(this).getUserId();

        topPager = findViewById(R.id.canvas_top);
        bottomPager = findViewById(R.id.canvas_bottom);
        shoesPager = findViewById(R.id.canvas_shoes);
        askAi = findViewById(R.id.canvas_ask_ai);
        weatherChip = findViewById(R.id.canvas_weather);

        outerwear = new Layer(findViewById(R.id.canvas_outerwear), Focus.OUTERWEAR, true);
        bag = new Layer(findViewById(R.id.canvas_bag), Focus.BAG, false);
        layers.add(outerwear);
        layers.add(bag);
        for (AccessorySpot spot : AccessorySpot.values()) {
            Layer layer = new Layer(findViewById(spot.pagerId), Focus.ACCESSORIES, spot.right - spot.left == 1f);
            accessorySpots.put(spot, layer);
            layers.add(layer);
        }
        View stage = findViewById(R.id.canvas_layers);
        whenLaidOut(stage, this::placeLayers);

        findViewById(R.id.canvas_outerwear_button).setOnClickListener(v ->
                chooseLayer(outerwear, R.string.canvas_no_outerwear));
        findViewById(R.id.canvas_bag_button).setOnClickListener(v -> chooseLayer(bag, R.string.canvas_no_bags));
        findViewById(R.id.canvas_accessory_button).setOnClickListener(v -> chooseAccessoryTypes());
        getSupportFragmentManager().setFragmentResultListener(KEY_ACCESSORIES, this, (key, result) ->
                wear(result.getStringArrayList(OptionsSheet.RESULT_VALUES)));
        findViewById(R.id.canvas_focus_done).setOnClickListener(v -> setFocus(Focus.NONE));
        findViewById(R.id.canvas_focus_remove).setOnClickListener(v -> removeFocusedLayer());

        // Back (the arrow or the system gesture) first leaves the layer being chosen, then the screen.
        leaveFocusOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                setFocus(Focus.NONE);
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

        weatherChip.setOnClickListener(v -> offerWeatherTip(false));
        findViewById(R.id.canvas_next).setOnClickListener(v -> {
            if (weatherTipOffered) goNext();
            else offerWeatherTip(true);
        });
        getSupportFragmentManager().setFragmentResultListener(WeatherTipSheet.REQUEST_KEY, this, (key, result) -> {
            if (result.getBoolean(WeatherTipSheet.RESULT_ADD)) {
                List<String> types = new ArrayList<>(wornTypes);
                types.addAll(tipTypes);
                wear(types);
            } else if (tipFromNext) {
                goNext();
            }
        });

        loadWardrobe();
        loadWeather();
    }

    /** The save screen behind Next arrives with the next build step. */
    private void goNext() {
        Toast.makeText(this, R.string.coming_next_step, Toast.LENGTH_SHORT).show();
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
                List<ItemDto> tops = new ArrayList<>();
                List<ItemDto> bottoms = new ArrayList<>();
                List<ItemDto> shoes = new ArrayList<>();
                List<ItemDto> jackets = new ArrayList<>();
                List<ItemDto> bags = new ArrayList<>();
                accessories.clear();
                for (ItemDto item : items) {
                    if (item.category == null) continue; // not placed in a slot until the user chooses a type
                    switch (item.category) {
                        case Categories.TOP -> tops.add(item);
                        case Categories.BOTTOM -> bottoms.add(item);
                        case Categories.SHOES -> shoes.add(item);
                        case Categories.OUTERWEAR -> jackets.add(item);
                        case Categories.BAG -> bags.add(item);
                        case Categories.ACCESSORY -> {
                            if (AccessorySpot.of(item.type) != null) accessories.add(item);
                        }
                        default -> { }
                    }
                }
                if (tops.isEmpty() && bottoms.isEmpty() && shoes.isEmpty() && jackets.isEmpty()) {
                    findViewById(R.id.canvas_empty).setVisibility(View.VISIBLE);
                    return;
                }
                // Outerwear hangs from the top's shoulder line, so it follows every change of the top.
                setUpPager(topPager, tops, 1f, null, (image, item) ->
                        ((PieceImageView) image).setOnImageChanged(OutfitCanvasActivity.this::alignOuterwear));
                topPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageScrollStateChanged(int state) {
                        if (state == ViewPager2.SCROLL_STATE_IDLE) alignOuterwear();
                    }
                });
                setUpPager(bottomPager, bottoms, 1f, null, null);
                setUpPager(shoesPager, shoes, SHOES_SCALE, null, null);
                setUpPager(outerwear.pager, jackets, OUTERWEAR_SCALE, outerwear,
                        OutfitCanvasActivity.this::styleOuterwearPage);
                setUpPager(bag.pager, bags, 1f, bag, null);
                askAi.setEnabled(true);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.canvas_loading).setVisibility(View.GONE);
                Toast.makeText(OutfitCanvasActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * One slot or layer = one pager. In a base slot (layer == null) and in a wide layer, side padding
     * leaves room for the previous and next piece, faded and smaller; a layer shows them only in focus.
     */
    private void setUpPager(ViewPager2 pager, List<ItemDto> items, float slotScale, @Nullable Layer layer,
            @Nullable SlotPagerAdapter.PageStyle pageStyle) {
        // The side padding must be in place before the pages are laid out, or the first piece
        // would sit against the left edge instead of the center.
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        boolean wide = layer == null || layer.wide;
        int side = wide ? Math.round(getResources().getDisplayMetrics().widthPixels * SIDE_PEEK) : 0;
        pages.setPadding(side, 0, side, 0);
        pages.setClipToPadding(!wide);
        pages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new SlotPagerAdapter(items, pageStyle));
        pager.setPageTransformer((page, position) -> {
            float centered = 1 - Math.min(1f, Math.abs(position)); // 1 in the middle, 0 one page away
            float itemScale = page.getTag() instanceof Float f ? f : 1f;
            float neighborAlpha = layer == null || layer.group == focus ? NEIGHBOR_ALPHA : 0f;
            page.setAlpha(neighborAlpha + (1 - neighborAlpha) * centered);
            float scale = (NEIGHBOR_SCALE + (1 - NEIGHBOR_SCALE) * centered) * itemScale * slotScale;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });
    }

    /**
     * Outerwear shares one frame with the top and the bottom, and hangs from the shoulder line of the
     * top: a jacket may reach down to the end of the top's part of the frame, a long coat to the end of
     * the bottom. Only the left half of either is drawn: an open jacket over the left side of the
     * outfit, the clothes underneath still visible on the right.
     */
    private void styleOuterwearPage(ImageView image, ItemDto item) {
        boolean isLong = LONG_OUTERWEAR.contains(item.type);
        whenLaidOut(image, () -> {
            int width = image.getWidth();
            int height = image.getHeight();
            image.setPadding(0, 0, 0, isLong ? 0 : height - Math.round(height * TOP_SHARE));
            image.setPivotX(width / 2f);
            image.setClipBounds(new Rect(0, 0, width / 2, height));
            hang((PieceImageView) image, shoulderLine());
        });
    }

    /**
     * How far below the top of its slot the top that is showing starts: the line outerwear hangs from.
     * Zero while there is no top, or its image hasn't arrived yet.
     */
    private float shoulderLine() {
        RecyclerView pages = (RecyclerView) topPager.getChildAt(0);
        RecyclerView.ViewHolder page = pages.findViewHolderForAdapterPosition(topPager.getCurrentItem());
        if (page == null || !(page.itemView instanceof PieceImageView piece)) return 0f;
        return piece.imageTop(piece.getTag() instanceof Float scale ? scale : 1f);
    }

    /** Re-hangs the outerwear after the top underneath changed (another top, or its image just arrived). */
    private void alignOuterwear() {
        float shoulderLine = shoulderLine();
        RecyclerView pages = (RecyclerView) outerwear.pager.getChildAt(0);
        for (int i = 0; i < pages.getChildCount(); i++) {
            if (pages.getChildAt(i) instanceof PieceImageView piece) hang(piece, shoulderLine);
        }
    }

    private static void hang(PieceImageView piece, float shoulderLine) {
        piece.hangFrom(shoulderLine);
        piece.setPivotY(shoulderLine); // a resized jacket grows down from the shoulders, not up over them
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
        if (item != null && layer.pager.getAdapter() instanceof SlotPagerAdapter adapter) {
            int index = adapter.indexOf(item.id);
            if (index >= 0) layer.pager.setCurrentItem(index, false);
        }
        // An instant move doesn't re-run the page transformer, so the pages would keep the fading and
        // size of the place they were in before. Redo it once the pages are laid out.
        layer.pager.post(layer.pager::requestTransform);
    }

    private void takeOff(Layer layer) {
        layer.on = false;
        layer.pager.setVisibility(View.GONE);
    }

    /** Puts the layer on at this item, or takes it off when there is no item. */
    private void show(Layer layer, @Nullable ItemDto item) {
        if (item == null) takeOff(layer);
        else putOn(layer, item);
    }

    /** Outerwear / Bag: put the layer on if it isn't, then let the user swipe through it. */
    private void chooseLayer(Layer layer, @StringRes int nothingOwned) {
        if (layer.pager.getAdapter() == null || layer.pager.getAdapter().getItemCount() == 0) {
            Toast.makeText(this, nothingOwned, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!layer.on) putOn(layer, null);
        setFocus(layer.group);
    }

    /** Accessories: first choose which kinds to wear, from the kinds the closet has. */
    private void chooseAccessoryTypes() {
        ArrayList<OptionsSheet.Option> options = new ArrayList<>();
        Set<String> listed = new LinkedHashSet<>();
        for (ItemDto item : accessories) {
            if (listed.add(item.type)) options.add(new OptionsSheet.Option(item.type, item.typeLabel, null, 0));
        }
        if (options.isEmpty()) {
            Toast.makeText(this, R.string.canvas_no_accessories, Toast.LENGTH_SHORT).show();
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
        if (!wornTypes.isEmpty()) setFocus(Focus.ACCESSORIES);
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

    // ----- Focus: choosing one layer while the rest of the outfit is locked -----

    private void setFocus(Focus newFocus) {
        focus = newFocus;
        boolean locked = focus != Focus.NONE;
        for (ViewPager2 slot : List.of(topPager, bottomPager, shoesPager)) {
            slot.setUserInputEnabled(!locked);
            slot.setAlpha(locked ? LOCKED_ALPHA : 1f);
        }
        for (Layer layer : layers) {
            layer.pager.setUserInputEnabled(layer.group == focus);
            layer.pager.setAlpha(locked && layer.group != focus ? LOCKED_ALPHA : 1f);
            layer.pager.requestTransform(); // neighbors appear in focus and disappear after it
        }
        findViewById(R.id.canvas_tools).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.canvas_focus_bar).setVisibility(locked ? View.VISIBLE : View.GONE);
        findViewById(R.id.canvas_ask_ai_holder).setVisibility(locked ? View.GONE : View.VISIBLE);
        findViewById(R.id.canvas_next).setEnabled(!locked);
        weatherChip.setEnabled(!locked);
        leaveFocusOnBack.setEnabled(locked);

        TextView hint = findViewById(R.id.canvas_focus_hint);
        hint.setVisibility(locked ? View.VISIBLE : View.GONE);
        switch (focus) {
            case OUTERWEAR -> hint.setText(R.string.canvas_focus_outerwear);
            case BAG -> hint.setText(R.string.canvas_focus_bag);
            case ACCESSORIES -> hint.setText(R.string.canvas_focus_accessories);
            case NONE -> { }
        }
    }

    /** "Remove" in the focus bar: the layer being chosen leaves the outfit. */
    private void removeFocusedLayer() {
        switch (focus) {
            case OUTERWEAR -> takeOff(outerwear);
            case BAG -> takeOff(bag);
            case ACCESSORIES -> {
                wornTypes.clear();
                showAccessories(List.of());
            }
            case NONE -> { }
        }
        setFocus(Focus.NONE);
    }

    // ----- The weather tip -----

    /**
     * Opened by the weather chip or by Next: offers what the weather calls for, the closet has and the
     * outfit lacks. With nothing to offer, the chip says why and Next simply goes on.
     */
    private void offerWeatherTip(boolean fromNext) {
        List<String> called = weatherTypes();
        List<String> missing = new ArrayList<>(); // called for, and nothing is worn in that spot yet
        List<String> labels = new ArrayList<>();  // …and the closet has one
        List<String> offered = new ArrayList<>();
        for (String type : called) {
            if (accessorySpots.get(AccessorySpot.of(type)).on) continue;
            missing.add(type);
            for (ItemDto item : accessories) {
                if (type.equals(item.type)) {
                    offered.add(type);
                    labels.add(item.typeLabel.toLowerCase(Locale.ROOT));
                    break;
                }
            }
        }
        if (offered.isEmpty()) {
            if (fromNext) {
                goNext();
            } else {
                int message = missing.isEmpty() ? R.string.weather_tip_fine
                        : called == COLD_TYPES ? R.string.weather_tip_cold_none : R.string.weather_tip_hot_none;
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            }
            return;
        }
        tipTypes = offered;
        tipFromNext = fromNext;
        weatherTipOffered = true;
        WeatherTipSheet.newInstance(weatherChip.getText().toString(), called == COLD_TYPES,
                        String.join(getString(R.string.tags_joined_separator), labels))
                .show(getSupportFragmentManager(), WeatherTipSheet.REQUEST_KEY);
    }

    /** The accessory types today's weather calls for: COLD_TYPES, HOT_TYPES or nothing. */
    private List<String> weatherTypes() {
        if (weather == null || weather.feel == null) return List.of();
        boolean sunny = "clear".equals(weather.sky) || "partly cloudy".equals(weather.sky);
        return switch (weather.feel) {
            case "freezing", "cold" -> COLD_TYPES;
            case "hot" -> HOT_TYPES;
            case "warm" -> sunny ? HOT_TYPES : List.of();
            default -> List.of();
        };
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
                outerwear.on ? currentItem(outerwear.pager) : null, bag.on ? currentItem(bag.pager) : null, worn));
        api.generateOutfit(userId, body).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable GenerateResult look) {
                setStyling(false);
                if (look == null) return;
                moveTo(topPager, look.top);
                moveTo(bottomPager, look.bottom);
                moveTo(shoesPager, look.shoes);
                show(outerwear, look.outerwear);
                show(bag, look.bag);
                List<ItemDto> chosen = look.accessories == null ? List.of() : look.accessories;
                wornTypes.clear();
                for (ItemDto item : chosen) wornTypes.add(item.type);
                showAccessories(chosen);
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

    // ----- The stylist's memory of this session -----

    private void remember(String hint, GenerateResult look) {
        Map<String, Object> turn = new HashMap<>();
        turn.put("hint", hint);
        turn.put("look", lookIds(look.top, look.bottom, look.shoes, look.outerwear, look.bag,
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
            @Nullable ItemDto shoes, @Nullable ItemDto outer, @Nullable ItemDto bag, List<ItemDto> accessories) {
        Map<String, Object> ids = new HashMap<>();
        ids.put("topId", top == null ? null : top.id);
        ids.put("bottomId", bottom == null ? null : bottom.id);
        ids.put("shoesId", shoes == null ? null : shoes.id);
        ids.put("outerwearId", outer == null ? null : outer.id);
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

    /** Scrolls a slot to the piece the stylist chose (it is always one of this slot's own pieces). */
    private void moveTo(ViewPager2 pager, @Nullable ItemDto item) {
        if (item == null || !(pager.getAdapter() instanceof SlotPagerAdapter adapter)) return;
        int index = adapter.indexOf(item.id);
        if (index >= 0) pager.setCurrentItem(index, true);
    }

    /** While the stylist works, the Ask AI button can't be tapped and shows a spinner instead of its icon. */
    private void setStyling(boolean styling) {
        askAi.setEnabled(!styling);
        askAi.setImageResource(styling ? 0 : R.drawable.ic_sparkle);
        findViewById(R.id.canvas_styling).setVisibility(styling ? View.VISIBLE : View.GONE);
    }
}
