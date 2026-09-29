package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mixyn.app.R;

/** Clothes tab: the empty-closet state (the item grid comes with uploads). */
public class ClothesFragment extends Fragment {

    public ClothesFragment() {
        super(R.layout.fragment_clothes);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.clothes_add).setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.coming_next_step, Toast.LENGTH_SHORT).show());
    }
}
