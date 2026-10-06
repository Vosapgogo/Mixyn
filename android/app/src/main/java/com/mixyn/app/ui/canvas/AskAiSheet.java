package com.mixyn.app.ui.canvas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;
import com.mixyn.app.ui.components.MixynTextField;

/** "Ask AI": an optional hint for the stylist ("burgundy", "cozy", "my black blazer") and Generate. */
public class AskAiSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "ask_ai";
    public static final String RESULT_HINT = "hint";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_ask_ai, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MixynTextField hint = view.findViewById(R.id.ask_ai_hint);
        view.findViewById(R.id.ask_ai_generate).setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString(RESULT_HINT, hint.getValue());
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        });
    }
}
