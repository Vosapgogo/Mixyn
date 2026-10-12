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
import com.mixyn.app.api.dto.OutfitDto;

import java.util.ArrayList;
import java.util.List;

/** Shows the saved outfits as a grid of their pictures. */
public class OutfitsAdapter extends RecyclerView.Adapter<OutfitsAdapter.Holder> {

    public interface OnOutfitClick {
        void onClick(OutfitDto outfit);
    }

    private final List<OutfitDto> outfits = new ArrayList<>();
    private final OnOutfitClick onOutfitClick;

    public OutfitsAdapter(OnOutfitClick onOutfitClick) {
        this.onOutfitClick = onOutfitClick;
    }

    public void setOutfits(List<OutfitDto> newOutfits) {
        outfits.clear();
        outfits.addAll(newOutfits);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_outfit, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OutfitDto outfit = outfits.get(position);
        Glide.with(holder.image).load(ApiClient.imageUrl(outfit.imageUrl)).into(holder.image);
        holder.image.setOnClickListener(v -> onOutfitClick.onClick(outfit));
    }

    @Override
    public int getItemCount() {
        return outfits.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.outfit_image);
        }
    }
}
