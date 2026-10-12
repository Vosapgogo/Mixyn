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
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.additem.AddItemActivity;
import com.mixyn.app.ui.components.FilterSheet;
import com.mixyn.app.ui.item.ItemDetailActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Clothes tab: the wardrobe grid, or the empty-closet state. Reloads every time it becomes visible.
 * The search field and the filter in Home's header narrow the grid down (see WardrobeFilter).
 */
public class ClothesFragment extends Fragment implements WardrobeFragment.Tab {

    private static final int COLUMNS = 3;
    private static final String KEY_FILTER = "clothes_filter";

    private ClothesAdapter adapter;
    private RecyclerView grid;
    private View empty;
    private View noMatch;
    private View loading;
    private TextView error;
    /** The whole wardrobe as it was loaded, or null until it has arrived. */
    @Nullable
    private List<ItemDto> wardrobe;

    public ClothesFragment() {
        super(R.layout.fragment_clothes);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        grid = view.findViewById(R.id.clothes_grid);
        empty = view.findViewById(R.id.clothes_empty);
        noMatch = view.findViewById(R.id.clothes_no_match);
        loading = view.findViewById(R.id.clothes_loading);
        error = view.findViewById(R.id.clothes_error);

        adapter = new ClothesAdapter(item -> ItemDetailActivity.start(requireContext(), item));
        grid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        grid.setAdapter(adapter);
        view.findViewById(R.id.clothes_add).setOnClickListener(v -> AddItemActivity.start(requireContext()));
        view.findViewById(R.id.clothes_clear_filters).setOnClickListener(v -> home().clearFilters());
        // The filter sheet answers here with what was ticked.
        getChildFragmentManager().setFragmentResultListener(KEY_FILTER, getViewLifecycleOwner(), (key, result) -> {
            home().clothesFilter().read(result);
            home().filterChanged();
            showMatches();
        });
    }

    private WardrobeFragment home() {
        return (WardrobeFragment) requireParentFragment();
    }

    @Override
    public void openFilter() {
        if (wardrobe == null) return; // the choices are made from the clothes, and they aren't here yet
        FilterSheet.newInstance(KEY_FILTER, home().clothesFilter().groupsForClothes(requireContext(), wardrobe))
                .show(getChildFragmentManager(), KEY_FILTER);
    }

    /** Puts the pieces that pass the filter and the search into the grid. */
    @Override
    public void showMatches() {
        if (wardrobe == null || getView() == null) return;
        WardrobeFilter filter = home().clothesFilter();
        List<ItemDto> matching = new ArrayList<>();
        for (ItemDto item : wardrobe) {
            if (filter.matches(requireContext(), item)) matching.add(item);
        }
        adapter.setItems(matching);
        show(wardrobe.isEmpty() ? empty : matching.isEmpty() ? noMatch : grid);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadItems(); // also picks up an item added a moment ago
    }

    private void loadItems() {
        long userId = new SessionStore(requireContext()).getUserId();
        ApiClient.get(requireContext()).items(userId).enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable List<ItemDto> items) {
                if (getView() == null || items == null) return;
                wardrobe = items;
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
