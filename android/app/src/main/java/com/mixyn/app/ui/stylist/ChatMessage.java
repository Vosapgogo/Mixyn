package com.mixyn.app.ui.stylist;

import androidx.annotation.Nullable;

import com.mixyn.app.api.dto.ChatReply;
import com.mixyn.app.api.dto.GenerateResult;
import com.mixyn.app.api.dto.ItemDto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One message of the Stylist chat: what the user wrote, or the stylist's answer with what it showed. */
class ChatMessage {

    final boolean fromUser;
    final String text;
    final List<GenerateResult> looks;
    final List<ItemDto> items;
    /** A request that failed (no connection…): shown, but not sent back to the stylist as part of the chat. */
    final boolean failed;

    private ChatMessage(boolean fromUser, String text, @Nullable List<GenerateResult> looks,
            @Nullable List<ItemDto> items, boolean failed) {
        this.fromUser = fromUser;
        this.text = text;
        this.looks = looks == null ? List.of() : looks;
        this.items = items == null ? List.of() : items;
        this.failed = failed;
    }

    static ChatMessage user(String text) {
        return new ChatMessage(true, text, null, null, false);
    }

    static ChatMessage stylist(ChatReply reply) {
        return new ChatMessage(false, reply.reply == null ? "" : reply.reply, reply.looks, reply.items, false);
    }

    static ChatMessage failed(String message) {
        return new ChatMessage(false, message, null, null, true);
    }

    /** The message as the backend wants it in the chat so far: role, text, and the ids of what was shown. */
    Map<String, Object> asTurn() {
        Map<String, Object> turn = new HashMap<>();
        turn.put("role", fromUser ? "user" : "stylist");
        turn.put("text", text);
        if (!fromUser) {
            List<Map<String, Object>> lookIds = new ArrayList<>();
            for (GenerateResult look : looks) lookIds.add(lookIds(look));
            List<Long> itemIds = new ArrayList<>();
            for (ItemDto item : items) itemIds.add(item.id);
            turn.put("looks", lookIds);
            turn.put("itemIds", itemIds);
        }
        return turn;
    }

    private static Map<String, Object> lookIds(GenerateResult look) {
        Map<String, Object> ids = new HashMap<>();
        ids.put("topId", look.top == null ? null : look.top.id);
        ids.put("bottomId", look.bottom == null ? null : look.bottom.id);
        ids.put("shoesId", look.shoes == null ? null : look.shoes.id);
        ids.put("outerwearId", look.outerwear == null ? null : look.outerwear.id);
        ids.put("underId", look.under == null ? null : look.under.id);
        ids.put("bagId", look.bag == null ? null : look.bag.id);
        List<Long> accessoryIds = new ArrayList<>();
        if (look.accessories != null) {
            for (ItemDto item : look.accessories) accessoryIds.add(item.id);
        }
        ids.put("accessoryIds", accessoryIds);
        return ids;
    }
}
