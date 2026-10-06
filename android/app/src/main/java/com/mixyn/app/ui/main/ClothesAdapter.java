package com.mixyn.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.ItemDto;

import java.util.ArrayList;
import java.util.List;

/** Shows the wardrobe as a grid of cutouts. */
public class ClothesAdapter extends RecyclerView.Adapter<ClothesAdapter.Holder> {

    public interface OnItemClick {
        void onClick(ItemDto item);
    }

    private final List<ItemDto> items = new ArrayList<>();
    private final OnItemClick onItemClick;

    public ClothesAdapter(OnItemClick onItemClick) {
        this.onItemClick = onItemClick;
    }

    public void setItems(List<ItemDto> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_clothes, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ItemDto item = items.get(position);
        Glide.with(holder.image).load(ApiClient.imageUrl(item.imageUrl)).into(holder.image);
        holder.image.setScaleX(item.scale / 100f); // the size chosen in "Edit photo"
        holder.image.setScaleY(item.scale / 100f);
        holder.itemView.setOnClickListener(v -> onItemClick.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.clothes_image);
        }
    }
}
