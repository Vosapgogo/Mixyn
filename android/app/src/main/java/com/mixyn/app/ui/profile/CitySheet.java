package com.mixyn.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;
import com.mixyn.app.api.dto.CityDto;
import com.mixyn.app.ui.components.CityPicker;
import com.mixyn.app.ui.components.MixynTextField;

/**
 * "Change city" on City for weather: a city field with suggestions. Save city hands the picked city to
 * the screen through the Fragment Result API; the screen saves it.
 */
public class CitySheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "change_city";
    public static final String RESULT_NAME = "name";
    public static final String RESULT_COUNTRY = "country";
    public static final String RESULT_LATITUDE = "latitude";
    public static final String RESULT_LONGITUDE = "longitude";

    @Nullable
    private CityPicker cityPicker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_city, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MixynTextField field = view.findViewById(R.id.city_sheet_field);
        cityPicker = new CityPicker(requireActivity(), field);
        view.findViewById(R.id.city_sheet_save).setOnClickListener(v -> {
            CityDto city = cityPicker.getSelected();
            if (city == null) {
                field.setError(getString(R.string.error_city_required));
                return;
            }
            Bundle result = new Bundle();
            result.putString(RESULT_NAME, city.name);
            result.putString(RESULT_COUNTRY, city.country);
            result.putDouble(RESULT_LATITUDE, city.latitude);
            result.putDouble(RESULT_LONGITUDE, city.longitude);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        if (cityPicker != null) cityPicker.release();
        super.onDestroyView();
    }
}
