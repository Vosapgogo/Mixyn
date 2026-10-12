package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.components.FilterSheet;
import com.mixyn.app.ui.outfit.OutfitActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Outfits tab: the saved outfits, newest first, or the empty state. Tapping one opens its card.
 * Reloads every time it becomes visible, so an outfit saved or deleted a moment ago is already right.
 * The search field and the filter in Home's header narrow the grid down (see WardrobeFilter).
 */
public class OutfitsFragment extends Fragment implements WardrobeFragment.Tab {

    private static final int COLUMNS = 2;
    private static final String KEY_FILTER = "outfits_filter";

    private OutfitsAdapter adapter;
    private RecyclerView grid;
    private View empty;
    private View noMatch;
    private View loading;
    private TextView error;
    /** Every saved outfit as it was loaded, or null until they have arrived. */
    @Nullable
    private List<OutfitDto> saved;

    public OutfitsFragment() {
        super(R.layout.fragment_outfits);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        grid = view.findViewById(R.id.outfits_grid);
        empty = view.findViewById(R.id.outfits_empty);
        noMatch = view.findViewById(R.id.outfits_no_match);
        loading = view.findViewById(R.id.outfits_loading);
        error = view.findViewById(R.id.outfits_error);

        adapter = new OutfitsAdapter(outfit -> OutfitActivity.start(requireContext(), outfit));
        grid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        grid.setAdapter(adapter);
        view.findViewById(R.id.outfits_clear_filters).setOnClickListener(v -> home().clearFilters());
        // The filter sheet answers here with what was ticked.
        getChildFragmentManager().setFragmentResultListener(KEY_FILTER, getViewLifecycleOwner(), (key, result) -> {
            home().outfitsFilter().read(result);
            home().filterChanged();
            showMatches();
        });
    }

    private WardrobeFragment home() {
        return (WardrobeFragment) requireParentFragment();
    }

    @Override
    public void openFilter() {
        if (saved == null) return; // the choices are made from the outfits, and they aren't here yet
        FilterSheet.newInstance(KEY_FILTER, home().outfitsFilter().groupsForOutfits(requireContext(), saved))
                .show(getChildFragmentManager(), KEY_FILTER);
    }

    /** Puts the outfits that pass the filter and the search into the grid. */
    @Override
    public void showMatches() {
        if (saved == null || getView() == null) return;
        WardrobeFilter filter = home().outfitsFilter();
        List<OutfitDto> matching = new ArrayList<>();
        for (OutfitDto outfit : saved) {
            if (filter.matches(requireContext(), outfit)) matching.add(outfit);
        }
        adapter.setOutfits(matching);
        show(saved.isEmpty() ? empty : matching.isEmpty() ? noMatch : grid);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadOutfits();
    }

    private void loadOutfits() {
        long userId = new SessionStore(requireContext()).getUserId();
        ApiClient.get(requireContext()).outfits(userId).enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable List<OutfitDto> outfits) {
                if (getView() == null || outfits == null) return;
                saved = outfits;
                showMatches();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                if (getView() == null) return;
                error.setText(message);
                show(error);
            }
        });
    }

    /** Exactly one of grid / empty state / nothing matches / error is visible. */
    private void show(View visible) {
        for (View v : new View[] {grid, empty, noMatch, error, loading}) {
            v.setVisibility(v == visible ? View.VISIBLE : View.GONE);
        }
    }
}
