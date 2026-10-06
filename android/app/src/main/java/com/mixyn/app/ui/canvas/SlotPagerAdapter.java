package com.mixyn.app.ui.canvas;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.ItemDto;

import java.util.List;

/** The pages of one canvas slot: every item of one category (all tops, all bottoms…). */
public class SlotPagerAdapter extends RecyclerView.Adapter<SlotPagerAdapter.Holder> {

    private final List<ItemDto> items;

    public SlotPagerAdapter(List<ItemDto> items) {
        this.items = items;
    }

    public ItemDto getItem(int position) {
        return items.get(position);
    }

    /** Position of the item with this id, or -1. */
    public int indexOf(long itemId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == itemId) return i;
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
        ItemDto item = items.get(position);
        Glide.with(holder.image).load(ApiClient.imageUrl(item.imageUrl)).into(holder.image);
        holder.image.setTag(item.scale / 100f); // the size from "Edit photo"; the page transformer applies it
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(ImageView image) {
            super(image);
            this.image = image;
        }
    }
}
