package com.mixyn.app.ui.components;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.CityDto;

import java.util.List;

import retrofit2.Call;

/**
 * City suggestions for a text field (sign-up, Edit profile, City for weather): as the user types, the
 * cities that match are offered in a dropdown (Open-Meteo through the backend), and only a city picked
 * from it counts, because the weather needs where the city is.
 */
public class CityPicker {

    private static final long SEARCH_DELAY_MS = 300;

    private final Activity activity;
    private final MixynTextField field;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingSearch;
    @Nullable
    private Call<List<CityDto>> call;
    @Nullable
    private CityDto selected;

    public CityPicker(Activity activity, MixynTextField field) {
        this.activity = activity;
        this.field = field;
        AutoCompleteTextView input = field.getInput();
        input.setOnItemClickListener((parent, view, position, id) -> {
            selected = (CityDto) parent.getItemAtPosition(position);
            field.setError(null);
            // Picking fills the field with the label, which would otherwise trigger one more search.
            if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Typing after a pick means the pick no longer matches the text.
                if (selected != null && !selected.toString().contentEquals(s)) selected = null;
                if (selected != null) return;
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                String query = s.toString().trim();
                if (query.length() < 2) return;
                pendingSearch = () -> search(query);
                handler.postDelayed(pendingSearch, SEARCH_DELAY_MS);
            }
        });
    }

    /** The city picked from the suggestions, or null while the text isn't one of them. */
    @Nullable
    public CityDto getSelected() {
        return selected;
    }

    /** Shows a city that is already chosen (the user's own), so it counts without picking it again. */
    public void setSelected(CityDto city) {
        selected = city;
        field.setValue(city.toString());
        field.getInput().dismissDropDown();
    }

    /** Stops a search still on its way; call it when the screen goes. */
    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (call != null) call.cancel();
    }

    private void search(String query) {
        if (call != null) call.cancel();
        call = ApiClient.get(activity).cities(query);
        call.enqueue(new ApiCallback<>(activity) {
            @Override
            protected void onSuccess(@Nullable List<CityDto> cities) {
                if (cities == null) return;
                AutoCompleteTextView input = field.getInput();
                input.setAdapter(new ArrayAdapter<>(activity, R.layout.item_dropdown, cities));
                if (input.hasFocus() && !cities.isEmpty()) input.showDropDown();
            }

            @Override
            protected void onError(@Nullable String code, @NonNull String message) {
                field.setError(activity.getString(R.string.error_cities));
            }
        });
    }
}
