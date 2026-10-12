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
import com.mixyn.app.ui.outfit.OutfitActivity;

import java.util.List;

/**
 * Outfits tab: the saved outfits, newest first, or the empty state. Tapping one opens its card.
 * Reloads every time it becomes visible, so an outfit saved or deleted a moment ago is already right.
 */
public class OutfitsFragment extends Fragment {

    private static final int COLUMNS = 2;

    private OutfitsAdapter adapter;
    private RecyclerView grid;
    private View empty;
    private View loading;
    private TextView error;

    public OutfitsFragment() {
        super(R.layout.fragment_outfits);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        grid = view.findViewById(R.id.outfits_grid);
        empty = view.findViewById(R.id.outfits_empty);
        loading = view.findViewById(R.id.outfits_loading);
        error = view.findViewById(R.id.outfits_error);

        adapter = new OutfitsAdapter(outfit -> OutfitActivity.start(requireContext(), outfit));
        grid.setLayoutManager(new GridLayoutManager(requireContext(), COLUMNS));
        grid.setAdapter(adapter);
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
                adapter.setOutfits(outfits);
                show(outfits.isEmpty() ? empty : grid);
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
