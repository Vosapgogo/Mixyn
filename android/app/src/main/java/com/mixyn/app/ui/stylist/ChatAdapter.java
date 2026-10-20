package com.mixyn.app.ui.stylist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.ui.components.ItemNames;

import java.util.ArrayList;
import java.util.List;

/**
 * The messages of the Stylist chat: the user's on the right, the stylist's on the left with the outfits
 * and pieces it shows, and, while an answer is on its way, a line saying the stylist is at work.
 */
class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    interface Listener {
        void onLookClick(GenerateResult look);

        void onItemClick(ItemDto item);
    }

    private static final int USER = 0;
    private static final int STYLIST = 1;
    private static final int THINKING = 2;
    /** Shown in the top's place, a dress takes the bottom's place as well. */
    private static final String DRESS = "DRESS";

    private final List<ChatMessage> messages;
    private final Listener listener;
    private boolean thinking;

    ChatAdapter(List<ChatMessage> messages, Listener listener) {
        this.messages = messages;
        this.listener = listener;
    }

    void setThinking(boolean thinking) {
        if (this.thinking == thinking) return;
        this.thinking = thinking;
        if (thinking) notifyItemInserted(messages.size());
        else notifyItemRemoved(messages.size());
    }

    @Override
    public int getItemCount() {
        return messages.size() + (thinking ? 1 : 0);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == messages.size()) return THINKING;
        return messages.get(position).fromUser ? USER : STYLIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == USER ? R.layout.item_chat_user
                : viewType == STYLIST ? R.layout.item_chat_stylist : R.layout.item_chat_thinking;
        return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false)) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == THINKING) return;
        ChatMessage message = messages.get(position);
        TextView text = holder.itemView.findViewById(R.id.chat_text);
        text.setText(message.text);
        if (message.fromUser) return;
        text.setTextColor(ContextCompat.getColor(text.getContext(), message.failed ? R.color.error : R.color.ink));
        text.setVisibility(message.text.isEmpty() ? View.GONE : View.VISIBLE);
        bindLooks(holder.itemView, message.looks);
        bindItems(holder.itemView, message.items);
    }

    /** One card per outfit, side by side. */
    private void bindLooks(View row, List<GenerateResult> looks) {
        LinearLayout cards = row.findViewById(R.id.chat_looks);
        cards.removeAllViews();
        row.findViewById(R.id.chat_looks_scroll).setVisibility(looks.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(row.getContext());
        for (int i = 0; i < looks.size(); i++) {
            GenerateResult look = looks.get(i);
            View card = inflater.inflate(R.layout.item_chat_look, cards, false);
            ((TextView) card.findViewById(R.id.look_title)).setText(row.getContext().getString(R.string.stylist_look, i + 1));
            ImageView top = card.findViewById(R.id.look_top);
            ImageView bottom = card.findViewById(R.id.look_bottom);
            load(top, look.top);
            load(bottom, look.bottom);
            load(card.findViewById(R.id.look_shoes), look.shoes);
            // A dress without bottoms fills the top's and the bottom's room together.
            if (look.bottom == null && look.top != null && DRESS.equals(look.top.type)) {
                bottom.setVisibility(View.GONE);
                ((LinearLayout.LayoutParams) top.getLayoutParams()).weight += ((LinearLayout.LayoutParams) bottom.getLayoutParams()).weight;
            }
            List<ItemDto> layers = new ArrayList<>();
            for (ItemDto layer : new ItemDto[] {look.outerwear, look.under, look.bag}) {
                if (layer != null) layers.add(layer);
            }
            if (look.accessories != null) layers.addAll(look.accessories);
            LinearLayout thumbs = card.findViewById(R.id.look_layers);
            int size = row.getResources().getDimensionPixelSize(R.dimen.chat_layer_size);
            for (ItemDto layer : layers) thumbs.addView(picture(thumbs, layer, size));
            thumbs.setVisibility(layers.isEmpty() ? View.GONE : View.VISIBLE);
            card.setOnClickListener(v -> listener.onLookClick(look));
            cards.addView(card);
        }
    }

    /** The pieces the stylist shows, side by side; each opens its item card. */
    private void bindItems(View row, List<ItemDto> items) {
        LinearLayout pictures = row.findViewById(R.id.chat_items);
        pictures.removeAllViews();
        row.findViewById(R.id.chat_items_scroll).setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        int size = row.getResources().getDimensionPixelSize(R.dimen.chat_piece_size);
        for (ItemDto item : items) {
            ImageView picture = picture(pictures, item, size);
            picture.setBackgroundResource(R.drawable.bg_card_white);
            int padding = row.getResources().getDimensionPixelSize(R.dimen.space_xs);
            picture.setPadding(padding, padding, padding, padding);
            picture.setOnClickListener(v -> listener.onItemClick(item));
            pictures.addView(picture);
        }
    }

    private static ImageView picture(ViewGroup parent, ItemDto item, int size) {
        ImageView picture = new ImageView(parent.getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMarginEnd(parent.getResources().getDimensionPixelSize(R.dimen.space_xs));
        picture.setLayoutParams(params);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setContentDescription(ItemNames.of(parent.getContext(), item));
        load(picture, item);
        return picture;
    }

    private static void load(ImageView image, @Nullable ItemDto item) {
        if (item == null) {
            Glide.with(image).clear(image);
            return;
        }
        Glide.with(image).load(ApiClient.imageUrl(item.imageUrl)).into(image);
    }
}
