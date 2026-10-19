package com.mixyn.app.ui.canvas;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.ItemDto;

import java.util.List;

/**
 * The pages of one canvas slot or layer: every item it can show (all tops, all bags, all scarves…).
 * The bottom slot under a dress also starts with an empty page: no bottoms, as the dress takes their place.
 */
public class SlotPagerAdapter extends RecyclerView.Adapter<SlotPagerAdapter.Holder> {

    /** Extra drawing rules for a page, applied each time an item is put on it (outerwear uses this). */
    public interface PageStyle {
        void apply(ImageView image, ItemDto item);
    }

    private final List<ItemDto> items;
    @Nullable
    private final PageStyle pageStyle;
    /** Whether the first page is the empty one, before the items. */
    private final boolean emptyFirst;

    public SlotPagerAdapter(List<ItemDto> items) {
        this(items, null);
    }

    public SlotPagerAdapter(List<ItemDto> items, @Nullable PageStyle pageStyle) {
        this(items, pageStyle, false);
    }

    public SlotPagerAdapter(List<ItemDto> items, @Nullable PageStyle pageStyle, boolean emptyFirst) {
        this.items = items;
        this.pageStyle = pageStyle;
        this.emptyFirst = emptyFirst;
    }

    /** The items on the pages, without the empty page. */
    public List<ItemDto> getItems() {
        return items;
    }

    public boolean hasEmptyFirst() {
        return emptyFirst;
    }

    /** The item on this page, or null for the empty page. */
    @Nullable
    public ItemDto getItem(int position) {
        if (emptyFirst) return position == 0 ? null : items.get(position - 1);
        return items.get(position);
    }

    /** Position of the item with this id, or -1. */
    public int indexOf(long itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == itemId) return emptyFirst ? i + 1 : i;
        }
        return -1;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder((ImageView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_canvas_piece, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ItemDto item = getItem(position);
        if (item == null) {
            Glide.with(holder.image).clear(holder.image);
            holder.image.setContentDescription(holder.image.getContext().getString(R.string.canvas_no_bottoms));
            holder.image.setTag(1f);
            return;
        }
        holder.image.setContentDescription(holder.image.getContext().getString(R.string.item_photo));
        if (pageStyle != null) pageStyle.apply(holder.image, item);
        // The canvas is later drawn into a picture of the outfit, and that can only be done with
        // bitmaps kept in ordinary memory (not the graphics card's, which Glide prefers).
        Glide.with(holder.image).load(ApiClient.imageUrl(item.imageUrl)).disallowHardwareConfig().into(holder.image);
        holder.image.setTag(item.scale / 100f); // the size from "Edit photo"; the page transformer applies it
    }

    @Override
    public int getItemCount() {
        return emptyFirst ? items.size() + 1 : items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(ImageView image) {
            super(image);
            this.image = image;
        }
    }
}
