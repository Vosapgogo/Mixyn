package com.mixyn.app.ui.canvas;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.Categories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Create outfit. The wardrobe is loaded once and split by category; each slot (top, bottom, shoes) is a
 * pager over its category. "Ask AI" sends an optional hint to the backend stylist and moves every slot
 * to the piece it chose. Outerwear is an optional layer drawn over the top.
 */
public class OutfitCanvasActivity extends BaseActivity {

    /** Share of the screen width left on each side of a piece, where its neighbors peek in. */
    private static final float SIDE_PEEK = 0.22f;
    private static final float NEIGHBOR_ALPHA = 0.25f;
    private static final float NEIGHBOR_SCALE = 0.8f;
    /** The outerwear layer sits a little left of center and slightly larger, so the top shows beside it. */
    private static final float OUTERWEAR_SHIFT = -0.08f;
    private static final float OUTERWEAR_SCALE = 1.12f;

    private MixynApi api;
    private long userId;
    private ViewPager2 topPager;
    private ViewPager2 bottomPager;
    private ViewPager2 shoesPager;
    private ImageView outerwearView;
    private MaterialButton outerwearButton;
    private MaterialButton askAi;

    private final List<ItemDto> outerwear = new ArrayList<>();
    /** Index into outerwear, or -1 for "no outer layer". */
    private int outerwearIndex = -1;

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
        outerwearView = findViewById(R.id.canvas_outerwear);
        outerwearButton = findViewById(R.id.canvas_outerwear_button);
        askAi = findViewById(R.id.canvas_ask_ai);

        findViewById(R.id.canvas_back).setOnClickListener(v -> finish());
        outerwearButton.setOnClickListener(v -> showOuterwear(outerwearIndex + 1 >= outerwear.size() ? -1 : outerwearIndex + 1));
        askAi.setOnClickListener(v -> new AskAiSheet().show(getSupportFragmentManager(), AskAiSheet.REQUEST_KEY));
        getSupportFragmentManager().setFragmentResultListener(AskAiSheet.REQUEST_KEY, this,
                (key, result) -> generate(result.getString(AskAiSheet.RESULT_HINT, "")));

        loadWardrobe();
    }

    // ----- Loading the wardrobe into the slots -----

    private void loadWardrobe() {
        api.items(userId).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable List<ItemDto> items) {
                findViewById(R.id.canvas_loading).setVisibility(View.GONE);
                if (items == null) return;
                List<ItemDto> tops = new ArrayList<>();
                List<ItemDto> bottoms = new ArrayList<>();
                List<ItemDto> shoes = new ArrayList<>();
                outerwear.clear();
                for (ItemDto item : items) {
                    if (item.category == null) continue; // not placed in a slot until the user chooses a type
                    switch (item.category) {
                        case Categories.TOP -> tops.add(item);
                        case Categories.BOTTOM -> bottoms.add(item);
                        case Categories.SHOES -> shoes.add(item);
                        case Categories.OUTERWEAR -> outerwear.add(item);
                        default -> { }
                    }
                }
                if (tops.isEmpty() && bottoms.isEmpty() && shoes.isEmpty() && outerwear.isEmpty()) {
                    findViewById(R.id.canvas_empty).setVisibility(View.VISIBLE);
                    return;
                }
                setUpSlot(topPager, tops);
                setUpSlot(bottomPager, bottoms);
                setUpSlot(shoesPager, shoes);
                outerwearButton.setVisibility(outerwear.isEmpty() ? View.GONE : View.VISIBLE);
                showOuterwear(-1);
                askAi.setEnabled(true);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                findViewById(R.id.canvas_loading).setVisibility(View.GONE);
                Toast.makeText(OutfitCanvasActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    /** One slot = one pager. Side padding lets the previous and next piece peek in, faded and smaller. */
    private void setUpSlot(ViewPager2 pager, List<ItemDto> items) {
        // The side padding must be in place before the pages are laid out, or the first piece
        // would sit against the left edge instead of the center.
        RecyclerView pages = (RecyclerView) pager.getChildAt(0);
        int side = Math.round(getResources().getDisplayMetrics().widthPixels * SIDE_PEEK);
        pages.setPadding(side, 0, side, 0);
        pages.setClipToPadding(false);
        pages.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new SlotPagerAdapter(items));
        pager.setPageTransformer((page, position) -> {
            float centered = 1 - Math.min(1f, Math.abs(position)); // 1 in the middle, 0 one page away
            float itemScale = page.getTag() instanceof Float f ? f : 1f;
            page.setAlpha(NEIGHBOR_ALPHA + (1 - NEIGHBOR_ALPHA) * centered);
            float scale = (NEIGHBOR_SCALE + (1 - NEIGHBOR_SCALE) * centered) * itemScale;
            page.setScaleX(scale);
            page.setScaleY(scale);
        });
    }

    private void showOuterwear(int index) {
        outerwearIndex = index;
        if (index < 0) {
            outerwearView.setVisibility(View.GONE);
            outerwearButton.setText(R.string.canvas_outerwear_add);
            return;
        }
        ItemDto item = outerwear.get(index);
        outerwearView.setVisibility(View.VISIBLE);
        outerwearView.setTranslationX(getResources().getDisplayMetrics().widthPixels * OUTERWEAR_SHIFT);
        outerwearView.setScaleX(OUTERWEAR_SCALE * item.scale / 100f);
        outerwearView.setScaleY(OUTERWEAR_SCALE * item.scale / 100f);
        Glide.with(this).load(ApiClient.imageUrl(item.imageUrl)).into(outerwearView);
        outerwearButton.setText(R.string.canvas_outerwear_change);
    }

    // ----- The AI stylist -----

    private void generate(String hint) {
        setStyling(true);
        Map<String, String> body = hint.isEmpty() ? Collections.emptyMap() : Collections.singletonMap("hint", hint);
        api.generateOutfit(userId, body).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable GenerateResult look) {
                setStyling(false);
                if (look == null) return;
                moveTo(topPager, look.top);
                moveTo(bottomPager, look.bottom);
                moveTo(shoesPager, look.shoes);
                showOuterwear(look.outerwear == null ? -1 : indexOfOuterwear(look.outerwear.id));
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

    /** Scrolls a slot to the piece the stylist chose (it is always one of this slot's own pieces). */
    private void moveTo(ViewPager2 pager, @Nullable ItemDto item) {
        if (item == null || !(pager.getAdapter() instanceof SlotPagerAdapter adapter)) return;
        int index = adapter.indexOf(item.id);
        if (index >= 0) pager.setCurrentItem(index, true);
    }

    private int indexOfOuterwear(long itemId) {
        for (int i = 0; i < outerwear.size(); i++) {
            if (outerwear.get(i).id == itemId) return i;
        }
        return -1;
    }

    private void setStyling(boolean styling) {
        askAi.setEnabled(!styling);
        askAi.setText(styling ? R.string.canvas_styling : R.string.canvas_ask_ai);
    }
}
