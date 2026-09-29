package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mixyn.app.R;
import com.mixyn.app.ui.base.BaseActivity;
import com.mixyn.app.ui.components.MixynBottomBar;

/** Hosts the tabs: Home (wardrobe) and Profile. Calendar and Friends are "coming soon". */
public class MainActivity extends BaseActivity implements MixynBottomBar.Listener {

    private MixynBottomBar bottomBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottomBar = findViewById(R.id.main_bottom_bar);
        bottomBar.setListener(this);
        if (savedInstanceState == null) show(new WardrobeFragment(), MixynBottomBar.Tab.HOME);
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
