package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;
import com.mixyn.app.R;
import com.mixyn.app.ui.components.MixynLogoHeader;
import com.mixyn.app.ui.components.MixynTextField;

/**
 * Home tab: "Mixyn" header and the Clothes / Outfits tabs.
 *
 * The header has two tools for the tab that is showing. The magnifier opens a search field under the
 * tabs. The three lines open that tab's filter sheet, and carry a dot while a filter is on. Each tab has
 * its own filter (clothes and outfits are filtered by different rules); it is kept here, because the
 * tabs' own screens are made anew on every switch. What is typed into search applies to both.
 */
public class WardrobeFragment extends Fragment {

    /** What a tab (Clothes, Outfits) does for the header above it. */
    interface Tab {
        /** Opens the filter sheet with what this tab's list can be filtered by. */
        void openFilter();

        /** The filter or the search text changed: show what matches now. */
        void showMatches();
    }

    private static final int TAB_CLOTHES = 0;
    private static final int TAB_OUTFITS = 1;
    private static final String ARG_OUTFITS = "outfits";
    private static final String STATE_TAB = "tab";
    private static final String STATE_CLOTHES_FILTER = "clothesFilter";
    private static final String STATE_OUTFITS_FILTER = "outfitsFilter";
    private static final String STATE_SEARCHING = "searching";

    private WardrobeFilter clothesFilter = new WardrobeFilter();
    private WardrobeFilter outfitsFilter = new WardrobeFilter();
    private int currentTab = TAB_CLOTHES;
    private MixynLogoHeader header;
    private MixynTextField search;

    public WardrobeFragment() {
        super(R.layout.fragment_wardrobe);
    }

    /** Home that starts on the Outfits tab instead of Clothes. */
    public static WardrobeFragment onOutfits() {
        Bundle args = new Bundle();
        args.putBoolean(ARG_OUTFITS, true);
        WardrobeFragment fragment = new WardrobeFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        header = view.findViewById(R.id.wardrobe_header);
        search = view.findViewById(R.id.wardrobe_search);
        if (savedInstanceState != null) {
            currentTab = savedInstanceState.getInt(STATE_TAB);
            clothesFilter = (WardrobeFilter) savedInstanceState.getSerializable(STATE_CLOTHES_FILTER);
            outfitsFilter = (WardrobeFilter) savedInstanceState.getSerializable(STATE_OUTFITS_FILTER);
            search.setVisibility(savedInstanceState.getBoolean(STATE_SEARCHING) ? View.VISIBLE : View.GONE);
        }

        header.setOnSecondActionClickListener(v -> showSearch(search.getVisibility() != View.VISIBLE));
        header.setOnActionClickListener(v -> {
            if (shownTab() != null) shownTab().openFilter();
        });
        search.getInput().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clothesFilter.setQuery(s.toString());
                outfitsFilter.setQuery(s.toString());
                if (shownTab() != null) shownTab().showMatches();
            }
        });
        // "Search" on the keyboard only puts the keyboard away: the list has followed every letter.
        search.getInput().setOnEditorActionListener((input, action, event) -> {
            if (action == EditorInfo.IME_ACTION_SEARCH) hideKeyboard();
            return action == EditorInfo.IME_ACTION_SEARCH;
        });

        TabLayout tabs = view.findViewById(R.id.wardrobe_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.tab_clothes));
        tabs.addTab(tabs.newTab().setText(R.string.tab_outfits));
        if (savedInstanceState != null) tabs.selectTab(tabs.getTabAt(currentTab)); // before the listener: nothing to do
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        if (savedInstanceState != null) {
            filterChanged();
            return; // Android has put the tab's screen back itself
        }
        boolean onOutfits = getArguments() != null && getArguments().getBoolean(ARG_OUTFITS);
        if (onOutfits) tabs.selectTab(tabs.getTabAt(TAB_OUTFITS)); // the listener above shows its content
        else showTab(TAB_CLOTHES);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
        outState.putSerializable(STATE_CLOTHES_FILTER, clothesFilter);
        outState.putSerializable(STATE_OUTFITS_FILTER, outfitsFilter);
        outState.putBoolean(STATE_SEARCHING, search != null && search.getVisibility() == View.VISIBLE);
    }

    private void showTab(int position) {
        currentTab = position;
        Fragment content = position == TAB_OUTFITS ? new OutfitsFragment() : new ClothesFragment();
        getChildFragmentManager().beginTransaction().replace(R.id.wardrobe_container, content).commit();
        filterChanged();
    }

    /** The screen of the tab that is showing, or null in the moment before it is there. */
    @Nullable
    private Tab shownTab() {
        return getChildFragmentManager().findFragmentById(R.id.wardrobe_container) instanceof Tab tab ? tab : null;
    }

    // ----- For the tabs -----

    WardrobeFilter clothesFilter() {
        return clothesFilter;
    }

    WardrobeFilter outfitsFilter() {
        return outfitsFilter;
    }

    /** A tab's filter has new choices: the dot on the three lines says whether any are on. */
    void filterChanged() {
        WardrobeFilter shown = currentTab == TAB_OUTFITS ? outfitsFilter : clothesFilter;
        header.setActionBadge(shown.count() > 0);
    }

    /** "Clear filters" under an empty result: the shown tab's choices and the search text both go. */
    void clearFilters() {
        (currentTab == TAB_OUTFITS ? outfitsFilter : clothesFilter).clear();
        filterChanged();
        if (search.getValue().isEmpty()) {
            if (shownTab() != null) shownTab().showMatches();
        } else {
            search.setValue(""); // the text watcher shows the matches
        }
    }

    // ----- Search -----

    /** Opens the search field with the keyboard, or closes it and forgets what was typed. */
    private void showSearch(boolean show) {
        search.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            search.getInput().requestFocus();
            requireContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(search.getInput(), InputMethodManager.SHOW_IMPLICIT);
        } else {
            search.setValue("");
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        requireContext().getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.getInput().clearFocus();
    }
}
