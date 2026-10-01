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

import java.util.List;

/** Clothes tab: the wardrobe grid, or the empty-closet state. Reloads every time it becomes visible. */
public class ClothesFragment extends Fragment {

    private static final int COLUMNS = 3;

    private ClothesAdapter adapter;
    private RecyclerView grid;
    private View empty;
    private View loading;
    private TextView error;

    public ClothesFragment() {
        super(R.layout.fragment_clothes);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        grid = view.findViewById(R.id.clothes_grid);
        empty = view.findViewById(R.id.clothes_empty);
        loading = view.findViewById(R.id.clothes_loading);
        error = view.findViewById(R.id.clothes_error);

        adapter = new ClothesAdapter(item -> { /* the item card opens here in the next build step */ });
        grid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        grid.setAdapter(adapter);
        view.findViewById(R.id.clothes_add).setOnClickListener(v -> AddItemActivity.start(requireContext()));
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
                adapter.setItems(items);
                show(items.isEmpty() ? empty : grid);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                if (getView() == null) return;
                error.setText(message);
                show(error);
            }
        });
    }

    /** Exactly one of grid / empty state / error is visible. */
    private void show(View visible) {
        for (View v : new View[] {grid, empty, error, loading}) {
            v.setVisibility(v == visible ? View.VISIBLE : View.GONE);
        }
    }
}
