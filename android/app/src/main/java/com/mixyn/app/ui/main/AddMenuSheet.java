package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;

/** The central + menu: Add clothes, Create outfit, OutfitCHECK (coming soon). */
public class AddMenuSheet extends BottomSheetDialogFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_add_menu, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.menu_add_clothes).setOnClickListener(v -> toastAndClose(R.string.coming_next_step));
        view.findViewById(R.id.menu_create_outfit).setOnClickListener(v -> toastAndClose(R.string.coming_next_step));
        view.findViewById(R.id.menu_outfitcheck).setOnClickListener(v -> toastAndClose(R.string.coming_soon));
    }

    private void toastAndClose(int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        dismiss();
    }
}
