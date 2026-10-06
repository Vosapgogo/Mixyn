package com.mixyn.app.ui.components;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.mixyn.app.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One bottom sheet for every "choose from a fixed list" in the app:
 * chips with a single choice (color, season), chips with several choices (tags),
 * or a searchable list (item types: type the first letters and the list narrows).
 * The chosen values come back through the Fragment Result API under the given request key.
 */
public class OptionsSheet extends BottomSheetDialogFragment {

    public enum Mode { CHIPS_SINGLE, CHIPS_MULTI, SEARCH_LIST }

    /** One choice: the value sent to the backend, the text shown, and optional dot color / right-side hint. */
    public static class Option implements Serializable {
        public final String value;
        public final String label;
        @Nullable
        public final String hint;
        @ColorInt
        public final int dotColor; // 0 = no dot

        public Option(String value, String label, @Nullable String hint, @ColorInt int dotColor) {
            this.value = value;
            this.label = label;
            this.hint = hint;
            this.dotColor = dotColor;
        }
    }

    public static final String RESULT_VALUES = "values";
    private static final String ARG_KEY = "key";
    private static final String ARG_TITLE = "title";
    private static final String ARG_MODE = "mode";
    private static final String ARG_OPTIONS = "options";
    private static final String ARG_SELECTED = "selected";
    private static final String ARG_SEARCH_HINT = "searchHint";
    private static final String ARG_MAX = "max";

    private final ArrayList<String> selected = new ArrayList<>();
    private List<Option> options;
    private Mode mode;
    private ChipGroup chips;
    private LinearLayout list;
    private View empty;

    public static OptionsSheet newInstance(String requestKey, String title, Mode mode, ArrayList<Option> options,
            ArrayList<String> selected, @Nullable String searchHint) {
        return newInstance(requestKey, title, mode, options, selected, searchHint, Integer.MAX_VALUE);
    }

    /** @param maxSelected for CHIPS_MULTI: how many chips may be selected at once */
    public static OptionsSheet newInstance(String requestKey, String title, Mode mode, ArrayList<Option> options,
            ArrayList<String> selected, @Nullable String searchHint, int maxSelected) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, requestKey);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MODE, mode.name());
        args.putSerializable(ARG_OPTIONS, options);
        args.putStringArrayList(ARG_SELECTED, selected);
        args.putString(ARG_SEARCH_HINT, searchHint);
        args.putInt(ARG_MAX, maxSelected);
        OptionsSheet sheet = new OptionsSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_options, container, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        mode = Mode.valueOf(args.getString(ARG_MODE));
        options = (ArrayList<Option>) args.getSerializable(ARG_OPTIONS);
        if (savedInstanceState == null) selected.addAll(args.getStringArrayList(ARG_SELECTED));

        ((TextView) view.findViewById(R.id.options_title)).setText(args.getString(ARG_TITLE));
        chips = view.findViewById(R.id.options_chips);
        list = view.findViewById(R.id.options_list);
        empty = view.findViewById(R.id.options_empty);

        if (mode == Mode.SEARCH_LIST) {
            chips.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            // A fixed height keeps the sheet from jumping while the list narrows.
            view.findViewById(R.id.options_scroll).getLayoutParams().height =
                    getResources().getDimensionPixelSize(R.dimen.sheet_list_max_height);
            MixynTextField search = view.findViewById(R.id.options_search);
            search.setVisibility(View.VISIBLE);
            search.getInput().setHint(args.getString(ARG_SEARCH_HINT));
            search.getInput().addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    showList(s.toString());
                }
            });
            showList("");
        } else {
            for (Option option : options) chips.addView(makeChip(option));
            if (mode == Mode.CHIPS_MULTI) {
                view.findViewById(R.id.options_actions).setVisibility(View.VISIBLE);
                view.findViewById(R.id.options_clear).setOnClickListener(v -> {
                    selected.clear();
                    for (int i = 0; i < chips.getChildCount(); i++) chips.getChildAt(i).setSelected(false);
                });
                view.findViewById(R.id.options_done).setOnClickListener(v -> finishWithResult());
            }
        }
    }

    /** A chip is a plain TextView styled as Widget.Mixyn.Chip; "selected" is its normal view state. */
    private TextView makeChip(Option option) {
        TextView chip = new TextView(requireContext(), null, 0, R.style.Widget_Mixyn_Chip);
        chip.setLayoutParams(new ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.chip_height)));
        chip.setText(option.label);
        if (option.dotColor != 0) {
            chip.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    ColorDots.dot(requireContext(), option.dotColor, R.dimen.chip_dot_size), null, null, null);
        }
        chip.setSelected(selected.contains(option.value));
        chip.setOnClickListener(v -> {
            if (mode == Mode.CHIPS_SINGLE) {
                selected.clear();
                selected.add(option.value);
                finishWithResult(); // one tap is the whole decision
            } else {
                boolean nowSelected = !chip.isSelected();
                int max = requireArguments().getInt(ARG_MAX, Integer.MAX_VALUE);
                if (nowSelected && selected.size() >= max) {
                    Toast.makeText(requireContext(), getString(R.string.item_color_limit, max), Toast.LENGTH_SHORT).show();
                    return;
                }
                chip.setSelected(nowSelected);
                if (nowSelected) selected.add(option.value);
                else selected.remove(option.value);
            }
        });
        return chip;
    }

    /** Rebuilds the list with the options whose name contains the typed text; names starting with it first. */
    private void showList(String typed) {
        String q = typed.trim().toLowerCase(Locale.ROOT);
        List<Option> matches = new ArrayList<>();
        for (Option o : options) if (o.label.toLowerCase(Locale.ROOT).startsWith(q)) matches.add(o);
        for (Option o : options) {
            String label = o.label.toLowerCase(Locale.ROOT);
            if (!label.startsWith(q) && label.contains(q)) matches.add(o);
        }
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Option option : matches) {
            View row = inflater.inflate(R.layout.item_option_row, list, false);
            ((TextView) row.findViewById(R.id.option_label)).setText(option.label);
            ((TextView) row.findViewById(R.id.option_hint)).setText(option.hint);
            row.setOnClickListener(v -> {
                selected.clear();
                selected.add(option.value);
                finishWithResult();
            });
            list.addView(row);
        }
        empty.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void finishWithResult() {
        Bundle result = new Bundle();
        result.putStringArrayList(RESULT_VALUES, new ArrayList<>(selected));
        getParentFragmentManager().setFragmentResult(requireArguments().getString(ARG_KEY), result);
        dismiss();
    }
}
