package com.mixyn.app.ui.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mixyn.app.R;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.MixynBottomBar;

/** Hosts the tabs: Home (wardrobe) and Profile. Calendar and Friends are "coming soon". */
public class MainActivity extends BaseActivity implements MixynBottomBar.Listener {

    private static final String EXTRA_TAB = "tab";

    private MixynBottomBar bottomBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomBar = findViewById(R.id.main_bottom_bar);
        bottomBar.setListener(this);
        if (savedInstanceState == null) show(new WardrobeFragment(), MixynBottomBar.Tab.HOME);
        openRequestedTab(getIntent());
    }

    /** Other screens with a bottom bar jump here with the tab they want. */
    public static Intent tabIntent(Context context, MixynBottomBar.Tab tab) {
        return new Intent(context, MainActivity.class)
                .putExtra(EXTRA_TAB, tab.name())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        openRequestedTab(intent);
    }

    private void openRequestedTab(Intent intent) {
        String tab = intent.getStringExtra(EXTRA_TAB);
        if (tab != null) {
            intent.removeExtra(EXTRA_TAB);
            onTabClick(MixynBottomBar.Tab.valueOf(tab));
        }
    }

    @Override
    protected boolean drawsBehindNavigationBar() {
        return true;
    }

    @Override
    public void onTabClick(MixynBottomBar.Tab tab) {
        switch (tab) {
            case HOME -> show(new WardrobeFragment(), tab);
            case PROFILE -> show(new ProfileFragment(), tab);
            default -> Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAddClick() {
        new AddMenuSheet().show(getSupportFragmentManager(), "add_menu");
    }

    private void show(Fragment fragment, MixynBottomBar.Tab tab) {
        getSupportFragmentManager().beginTransaction().replace(R.id.main_container, fragment).commit();
        bottomBar.setSelectedTab(tab);
    }
}
