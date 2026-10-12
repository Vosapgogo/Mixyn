package com.mixyn.app.ui.components;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.ChipGroup;
import com.mixyn.app.R;
import com.mixyn.app.ui.components.OptionsSheet.Option;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * A bottom sheet with several groups of chips, any number of them chosen in each: the filters of a
 * list. Nothing changes until the user taps Show; then every group's chosen values come back through
 * the Fragment Result API, each list under its group's key. Clear all unticks everything.
 */
public class FilterSheet extends BottomSheetDialogFragment {

    /** One group: its key in the result, its title, an optional line under it, the chips, the chosen ones. */
    public static class Group implements Serializable {
        public final String key;
        public final String title;
        @Nullable
        public final String hint;
        public final ArrayList<Option> options;
        public final ArrayList<String> selected;

        public Group(String key, String title, @Nullable String hint, ArrayList<Option> options,
                ArrayList<String> selected) {
            this.key = key;
            this.title = title;
            this.hint = hint;
            this.options = options;
            this.selected = selected;
        }
    }

    private static final String ARG_KEY = "key";
    private static final String ARG_GROUPS = "groups";
    private static final String STATE_CHOSEN = "chosen";

    /** What is ticked right now, per group key. The sheet's own copy: the caller's lists stay as they were. */
    private HashMap<String, ArrayList<String>> chosen = new HashMap<>();
    private LinearLayout groupsView;

    /** Groups without options (nothing to choose from yet) are left out of the sheet. */
    public static FilterSheet newInstance(String requestKey, ArrayList<Group> groups) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, requestKey);
        args.putSerializable(ARG_GROUPS, groups);
        FilterSheet sheet = new FilterSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_filter, container, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ArrayList<Group> groups = (ArrayList<Group>) requireArguments().getSerializable(ARG_GROUPS);
        if (savedInstanceState != null) {
            chosen = (HashMap<String, ArrayList<String>>) savedInstanceState.getSerializable(STATE_CHOSEN);
        } else {
            for (Group group : groups) chosen.put(group.key, new ArrayList<>(group.selected));
        }

        groupsView = view.findViewById(R.id.filter_groups);
        for (Group group : groups) {
            if (!group.options.isEmpty()) addGroup(group);
        }
        view.findViewById(R.id.filter_clear).setOnClickListener(v -> clearAll());
        view.findViewById(R.id.filter_show).setOnClickListener(v -> finishWithResult());
    }

    @Override
    public void onStart() {
        super.onStart();
        // Open at full height straight away, so the buttons at the bottom are on screen.
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) requireView().getParent());
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_CHOSEN, chosen);
    }

    /** A small title, an optional explaining line, and the group's chips flowing over as many rows as they need. */
    private void addGroup(Group group) {
        TextView title = new TextView(requireContext(), null, 0, R.style.Widget_Mixyn_CardLabel);
        title.setText(group.title);
        groupsView.addView(title, withTopMargin(R.dimen.space_l));

        if (group.hint != null) {
            TextView hint = new TextView(requireContext());
            TextViewCompat.setTextAppearance(hint, R.style.TextAppearance_Mixyn_Caption);
            hint.setText(group.hint);
            groupsView.addView(hint, withTopMargin(R.dimen.space_xxs));
        }

        ChipGroup chips = new ChipGroup(requireContext());
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xs);
        chips.setChipSpacingHorizontal(gap);
        chips.setChipSpacingVertical(gap);
        ArrayList<String> ticked = chosen.get(group.key);
        for (Option option : group.options) {
            TextView chip = OptionsSheet.chip(requireContext(), option);
            chip.setSelected(ticked.contains(option.value));
            chip.setOnClickListener(v -> {
                chip.setSelected(!chip.isSelected());
                if (chip.isSelected()) ticked.add(option.value);
                else ticked.remove(option.value);
            });
            chips.addView(chip);
        }
        groupsView.addView(chips, withTopMargin(R.dimen.space_s));
    }

    private LinearLayout.LayoutParams withTopMargin(int dimen) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = getResources().getDimensionPixelSize(dimen);
        return params;
    }

    private void clearAll() {
        for (ArrayList<String> ticked : chosen.values()) ticked.clear();
        for (int i = 0; i < groupsView.getChildCount(); i++) {
            if (!(groupsView.getChildAt(i) instanceof ChipGroup chips)) continue;
            for (int c = 0; c < chips.getChildCount(); c++) chips.getChildAt(c).setSelected(false);
        }
    }

    private void finishWithResult() {
        Bundle result = new Bundle();
        chosen.forEach(result::putStringArrayList);
        getParentFragmentManager().setFragmentResult(requireArguments().getString(ARG_KEY), result);
        dismiss();
    }
}
