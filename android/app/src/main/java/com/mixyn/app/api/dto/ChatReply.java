package com.mixyn.app.api.dto;

import androidx.annotation.Nullable;

import java.util.List;

/** The Stylist chat's answer: a short message, up to three outfits and up to twelve pieces. */
public class ChatReply {
    public String reply;
    /** True when the message wasn't about clothes: then there are no outfits or pieces. */
    public boolean offTopic;
    @Nullable
    public List<GenerateResult> looks;
    @Nullable
    public List<ItemDto> items;
}
