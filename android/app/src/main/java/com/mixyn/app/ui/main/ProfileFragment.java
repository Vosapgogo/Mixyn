package com.mixyn.app.ui.main;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.MixynApi;
import com.mixyn.app.api.dto.ColorDto;
import com.mixyn.app.api.dto.ItemDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.api.dto.UserDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.components.ColorDots;
import com.mixyn.app.ui.components.MixynLogoHeader;
import com.mixyn.app.ui.profile.CityWeatherActivity;
import com.mixyn.app.ui.profile.EditProfileActivity;
import com.mixyn.app.ui.profile.GoodbyeActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Profile tab (design page 19): the photo, the name and @username · city; three tiles (pieces, outfits
 * saved, days worn); the closet's palette, each color as wide as its share of the pieces, and the tags
 * worn most; then City for weather, Log out and Delete account, the last two asked about first. The
 * pencil opens Edit profile. It is all loaded again whenever the tab shows, so changes appear at once.
 */
public class ProfileFragment extends Fragment {

    private static final int MAX_PALETTE_COLORS = 8;
    private static final int MAX_TAGS = 3;

    private SessionStore session;
    private MixynApi api;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        session = new SessionStore(requireContext());
        api = ApiClient.get(requireContext());
        ((MixynLogoHeader) view.findViewById(R.id.profile_header))
                .setOnActionClickListener(v -> EditProfileActivity.start(requireContext()));
        view.findViewById(R.id.profile_city).setOnClickListener(v -> CityWeatherActivity.start(requireContext()));
        view.findViewById(R.id.profile_logout).setOnClickListener(v -> confirm(R.string.profile_log_out_title,
                R.string.profile_log_out_body, R.string.profile_log_out, this::logOut));
        view.findViewById(R.id.profile_delete).setOnClickListener(v -> confirm(R.string.profile_delete_title,
                R.string.profile_delete_body, R.string.profile_delete_confirm, this::deleteAccount));
        // What the phone already knows, until the backend answers.
        showName(session.getName(), null, session.getCity());
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        long userId = session.getUserId();
        api.me().enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable UserDto user) {
                if (user == null || getView() == null) return;
                session.update(user);
                showName(user.name, user.username, user.city);
                showAvatar(user.avatarUrl);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                // The name and city from the phone stay; the rest can wait for the next visit.
            }
        });
        api.items(userId).enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable List<ItemDto> items) {
                if (items != null && getView() != null) showCloset(items);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
            }
        });
        api.outfits(userId).enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable List<OutfitDto> outfits) {
                if (outfits == null || getView() == null) return;
                int days = 0;
                for (OutfitDto outfit : outfits) days += outfit.wornDays;
                ((TextView) requireView().findViewById(R.id.profile_outfits)).setText(String.valueOf(outfits.size()));
                ((TextView) requireView().findViewById(R.id.profile_days)).setText(String.valueOf(days));
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
            }
        });
    }

    private void showName(String name, @Nullable String username, String city) {
        View view = requireView();
        ((TextView) view.findViewById(R.id.profile_name)).setText(name);
        ((TextView) view.findViewById(R.id.profile_details)).setText(username == null ? city
                : getString(R.string.profile_username_city, username, city));
        ((TextView) view.findViewById(R.id.profile_city_value)).setText(city);
    }

    /** The photo, round; without one, the person icon on mocha. */
    private void showAvatar(@Nullable String url) {
        ImageView avatar = requireView().findViewById(R.id.profile_avatar);
        if (url == null) return;
        avatar.setPadding(0, 0, 0, 0);
        avatar.setImageTintList(null);
        Glide.with(this).load(ApiClient.imageUrl(url)).circleCrop().into(avatar);
    }

    /** The pieces tile, the palette and the tags, all from the clothes. */
    private void showCloset(List<ItemDto> items) {
        View view = requireView();
        ((TextView) view.findViewById(R.id.profile_pieces)).setText(String.valueOf(items.size()));

        Map<String, Integer> colorCounts = new HashMap<>();
        Map<String, String> hexes = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();
        for (ItemDto item : items) {
            if (item.colors != null) {
                for (ColorDto color : item.colors) {
                    colorCounts.merge(color.code, 1, Integer::sum);
                    hexes.put(color.code, color.hex);
                }
            }
            if (item.tags != null) {
                for (String tag : item.tags) tagCounts.merge(tag, 1, Integer::sum);
            }
        }

        LinearLayout palette = view.findViewById(R.id.profile_palette);
        palette.removeAllViews();
        palette.setClipToOutline(true); // the colors take the bar's rounded ends
        for (String code : mostFirst(colorCounts, MAX_PALETTE_COLORS)) {
            View stripe = new View(requireContext());
            stripe.setBackgroundColor(Color.parseColor(hexes.get(code)));
            palette.addView(stripe, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,
                    colorCounts.get(code)));
        }
        int paletteShown = colorCounts.isEmpty() ? View.GONE : View.VISIBLE;
        palette.setVisibility(paletteShown);
        view.findViewById(R.id.profile_palette_title).setVisibility(paletteShown);

        LinearLayout tags = view.findViewById(R.id.profile_tags);
        tags.removeAllViews();
        List<String> names = Arrays.asList(getResources().getStringArray(R.array.tag_names));
        TypedArray dots = getResources().obtainTypedArray(R.array.tag_colors);
        int stroke = getResources().getDimensionPixelSize(R.dimen.stroke_error);
        for (String tag : mostFirst(tagCounts, MAX_TAGS)) {
            int index = names.indexOf(tag);
            int color = index < 0 ? requireContext().getColor(R.color.ink_muted) : dots.getColor(index, 0);
            TextView pill = (TextView) LayoutInflater.from(requireContext()).inflate(R.layout.item_profile_tag, tags, false);
            pill.setText(tag);
            pill.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    ColorDots.dot(requireContext(), color, R.dimen.chip_dot_size), null, null, null);
            ((GradientDrawable) pill.getBackground().mutate()).setStroke(stroke, color);
            tags.addView(pill);
        }
        dots.recycle();
        tags.setVisibility(tagCounts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** The keys with the highest counts, highest first, at most this many. */
    private static List<String> mostFirst(Map<String, Integer> counts, int max) {
        List<String> keys = new ArrayList<>(counts.keySet());
        keys.sort((a, b) -> counts.get(b) - counts.get(a));
        return keys.subList(0, Math.min(max, keys.size()));
    }

    private void confirm(@StringRes int title, @StringRes int body, @StringRes int action, Runnable then) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton(R.string.profile_cancel, null)
                .setPositiveButton(action, (dialog, which) -> then.run())
                .show();
    }

    private void logOut() {
        // Tell the server to forget the token, but log out locally right away either way.
        api.logout("Bearer " + session.getToken()).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
            }
        });
        session.clear();
        GoodbyeActivity.start(requireContext(), false);
    }

    /** Only once the backend has deleted everything does the phone forget the account. */
    private void deleteAccount() {
        api.deleteAccount().enqueue(new ApiCallback<>(requireActivity()) {
            @Override
            protected void onSuccess(@Nullable Void nothing) {
                session.clear();
                GoodbyeActivity.start(requireActivity(), true);
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
