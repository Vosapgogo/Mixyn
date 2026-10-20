package com.mixyn.app.ui.stylist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.ChatReply;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.canvas.OutfitCanvasActivity;
import com.mixyn.app.ui.item.ItemDetailActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Stylist chat, opened from the bottom bar. The user writes a request in their own words; the
 * stylist answers with a message and, where it helps, outfit cards (each opens on the canvas, to be
 * changed and saved) or pictures of pieces (each opens its item card).
 *
 * The backend keeps no chat: every message goes out with the chat so far. The chat itself lives in
 * memory while the app runs, so leaving it for the canvas or an item card and coming back keeps it;
 * New chat starts over, and so does another account. Saved chat history is a later step.
 */
public class StylistChatActivity extends BaseActivity implements ChatAdapter.Listener {

    /** How much of the chat goes along with a new message (the backend takes at most this many). */
    private static final int MAX_HISTORY = 12;

    /** The chat of this app run, and whose it is. */
    private static final List<ChatMessage> chat = new ArrayList<>();
    private static long chatUserId = -1;

    private long userId;
    private ChatAdapter adapter;
    private RecyclerView messages;
    private EditText input;
    private boolean waiting;

    public static void start(Context context) {
        context.startActivity(new Intent(context, StylistChatActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stylist_chat);
        userId = new SessionStore(this).getUserId();
        if (userId != chatUserId) {
            chat.clear();
            chatUserId = userId;
        }

        messages = findViewById(R.id.stylist_messages);
        input = findViewById(R.id.stylist_input);
        LinearLayoutManager layout = new LinearLayoutManager(this);
        layout.setStackFromEnd(true);
        messages.setLayoutManager(layout);
        adapter = new ChatAdapter(chat, this);
        messages.setAdapter(adapter);

        findViewById(R.id.stylist_back).setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.stylist_new_chat).setOnClickListener(v -> newChat());
        findViewById(R.id.stylist_send).setOnClickListener(v -> send(input.getText().toString()));

        LinearLayout suggestions = findViewById(R.id.stylist_suggestions);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        for (String suggestion : getResources().getStringArray(R.array.stylist_suggestions)) {
            TextView chip = (TextView) LayoutInflater.from(this).inflate(R.layout.item_chat_suggestion, suggestions, false);
            chip.setText(suggestion);
            chip.setOnClickListener(v -> send(suggestion));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) chip.getLayoutParams();
            params.topMargin = gap;
            suggestions.addView(chip);
        }
        showIntro();
    }

    /** Before the first message: what the stylist does, and a few requests to start with. */
    private void showIntro() {
        findViewById(R.id.stylist_intro).setVisibility(chat.isEmpty() && !waiting ? View.VISIBLE : View.GONE);
    }

    private void newChat() {
        if (waiting) return;
        int count = chat.size();
        chat.clear();
        adapter.notifyItemRangeRemoved(0, count);
        showIntro();
    }

    private void send(String typed) {
        String text = typed.trim();
        if (text.isEmpty() || waiting) return;
        Map<String, Object> body = new HashMap<>();
        body.put("message", text);
        body.put("history", history());

        chat.add(ChatMessage.user(text));
        adapter.notifyItemInserted(chat.size() - 1);
        input.setText("");
        setWaiting(true);
        ApiClient.get(this).stylistChat(userId, body).enqueue(new ApiCallback<>(this) {
            @Override
            protected void onSuccess(@Nullable ChatReply reply) {
                setWaiting(false);
                if (reply != null) add(ChatMessage.stylist(reply));
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                setWaiting(false);
                add(ChatMessage.failed(message));
            }
        });
    }

    /** The chat so far, as the backend takes it: the latest messages, without the failed requests. */
    private List<Map<String, Object>> history() {
        List<Map<String, Object>> turns = new ArrayList<>();
        for (ChatMessage message : chat) {
            if (!message.failed) turns.add(message.asTurn());
        }
        return turns.size() <= MAX_HISTORY ? turns : turns.subList(turns.size() - MAX_HISTORY, turns.size());
    }

    private void add(ChatMessage message) {
        chat.add(message);
        adapter.notifyItemInserted(chat.size() - 1);
        messages.scrollToPosition(adapter.getItemCount() - 1);
    }

    /** While the stylist works: a line in the chat, and a spinner instead of the arrow on the send button. */
    private void setWaiting(boolean waiting) {
        this.waiting = waiting;
        adapter.setThinking(waiting);
        messages.scrollToPosition(adapter.getItemCount() - 1);
        ImageButton send = findViewById(R.id.stylist_send);
        send.setEnabled(!waiting);
        send.setImageResource(waiting ? 0 : R.drawable.ic_arrow_forward);
        findViewById(R.id.stylist_sending).setVisibility(waiting ? View.VISIBLE : View.GONE);
        showIntro();
    }

    /** An outfit card: the outfit goes onto the canvas as a new look, to be changed and saved there. */
    @Override
    public void onLookClick(GenerateResult look) {
        OutfitDto outfit = new OutfitDto();
        outfit.pieces = new ArrayList<>();
        addPiece(outfit, OutfitDto.Piece.TOP, look.top);
        addPiece(outfit, OutfitDto.Piece.BOTTOM, look.bottom);
        addPiece(outfit, OutfitDto.Piece.SHOES, look.shoes);
        addPiece(outfit, OutfitDto.Piece.OUTERWEAR, look.outerwear);
        addPiece(outfit, OutfitDto.Piece.UNDER, look.under);
        addPiece(outfit, OutfitDto.Piece.BAG, look.bag);
        if (look.accessories != null) {
            for (ItemDto item : look.accessories) addPiece(outfit, OutfitDto.Piece.ACCESSORY, item);
        }
        OutfitCanvasActivity.wear(this, outfit);
    }

    private static void addPiece(OutfitDto outfit, String slot, @Nullable ItemDto item) {
        if (item != null) outfit.pieces.add(new OutfitDto.Piece(slot, item));
    }

    @Override
    public void onItemClick(ItemDto item) {
        ItemDetailActivity.start(this, item);
    }
}
