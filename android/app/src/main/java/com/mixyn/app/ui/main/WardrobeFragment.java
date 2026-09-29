package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.mixyn.app.R;
import com.mixyn.app.ui.components.MixynLogoHeader;

/** Home tab: "Mixyn" header and the Clothes / Outfits / Collections tabs. */
public class WardrobeFragment extends Fragment {

    private static final int TAB_CLOTHES = 0;
    private static final int TAB_OUTFITS = 1;
    private static final int TAB_COLLECTIONS = 2;

    public WardrobeFragment() {
        super(R.layout.fragment_wardrobe);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MixynLogoHeader header = view.findViewById(R.id.wardrobe_header);
        header.setOnActionClickListener(v -> comingSoon());

        TabLayout tabs = view.findViewById(R.id.wardrobe_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.tab_clothes));
        tabs.addTab(tabs.newTab().setText(R.string.tab_outfits));
        tabs.addTab(tabs.newTab().setText(R.string.tab_collections));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            private int current = TAB_CLOTHES;

            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == TAB_COLLECTIONS) {
                    // Not in the PoC: say so and jump back to the tab the user was on.
                    comingSoon();
                    tabs.post(() -> tabs.selectTab(tabs.getTabAt(current)));
                    return;
                }
                current = tab.getPosition();
                showTab(current);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        if (savedInstanceState == null) showTab(TAB_CLOTHES);
    }

    private void showTab(int position) {
        Fragment content = position == TAB_OUTFITS ? new OutfitsFragment() : new ClothesFragment();
        getChildFragmentManager().beginTransaction().replace(R.id.wardrobe_container, content).commit();
    }

    private void comingSoon() {
        Toast.makeText(requireContext(), R.string.coming_soon, Toast.LENGTH_SHORT).show();
    }
}
