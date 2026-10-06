package com.mixyn.app.ui.canvas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;
import com.mixyn.app.ui.components.MixynTextField;

/**
 * "Ask AI": the user describes what they want — a word or a few sentences — and taps Generate.
 * From the second request on, the sheet shows that the stylist remembers the conversation and
 * offers "Start over" to forget it.
 */
public class AskAiSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "ask_ai";
    public static final String RESULT_HINT = "hint";
    /** True when the user tapped "Start over": forget the earlier requests, then use this one. */
    public static final String RESULT_START_OVER = "startOver";
    private static final String ARG_LAST_REQUEST = "lastRequest";

    private boolean startOver;

    /** @param lastRequest the previous request of this session, or null if this is the first one */
    public static AskAiSheet newInstance(@Nullable String lastRequest) {
        Bundle args = new Bundle();
        args.putString(ARG_LAST_REQUEST, lastRequest);
        AskAiSheet sheet = new AskAiSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_ask_ai, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MixynTextField hint = view.findViewById(R.id.ask_ai_hint);
        TextView body = view.findViewById(R.id.ask_ai_body);
        View memory = view.findViewById(R.id.ask_ai_memory);

        String lastRequest = requireArguments().getString(ARG_LAST_REQUEST);
        if (lastRequest != null) {
            // A follow-up: say so, and suggest the kind of short changes that now work.
            body.setText(R.string.ask_ai_memory);
            hint.getInput().setHint(R.string.ask_ai_hint_followup);
            memory.setVisibility(View.VISIBLE);
            ((TextView) view.findViewById(R.id.ask_ai_last)).setText(getString(R.string.ask_ai_last, lastRequest));
            view.findViewById(R.id.ask_ai_start_over).setOnClickListener(v -> {
                startOver = true;
                memory.setVisibility(View.GONE);
                body.setText(R.string.ask_ai_body);
                hint.getInput().setHint(R.string.ask_ai_hint);
            });
        }

        view.findViewById(R.id.ask_ai_generate).setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString(RESULT_HINT, hint.getValue());
            result.putBoolean(RESULT_START_OVER, startOver);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        });
    }
}
