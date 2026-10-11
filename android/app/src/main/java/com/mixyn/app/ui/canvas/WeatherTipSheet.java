package com.mixyn.app.ui.canvas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;

/**
 * Offers what the weather calls for and the outfit doesn't have yet: a beanie, scarf and gloves in the
 * cold, a cap or headscarf in the heat. The canvas decides what to offer; this sheet only asks.
 * The answer comes back through the Fragment Result API: RESULT_ADD is true for "Add to the outfit",
 * false for "Not now"; swiping the sheet away is no answer at all.
 */
public class WeatherTipSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "weather_tip";
    public static final String RESULT_ADD = "add";
    private static final String ARG_WEATHER = "weather";
    private static final String ARG_COLD = "cold";
    private static final String ARG_PIECES = "pieces";

    /**
     * @param weather the line from the weather chip, e.g. "4° · cold"
     * @param cold    true for the cold-weather tip, false for the hot-weather one
     * @param pieces  what would be added, as text: "beanie, scarf, gloves"
     */
    public static WeatherTipSheet newInstance(String weather, boolean cold, String pieces) {
        Bundle args = new Bundle();
        args.putString(ARG_WEATHER, weather);
        args.putBoolean(ARG_COLD, cold);
        args.putString(ARG_PIECES, pieces);
        WeatherTipSheet sheet = new WeatherTipSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_weather_tip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        boolean cold = args.getBoolean(ARG_COLD);
        TextView weather = view.findViewById(R.id.weather_tip_weather);
        weather.setText(args.getString(ARG_WEATHER));
        weather.setCompoundDrawablesRelativeWithIntrinsicBounds(cold ? R.drawable.ic_cloud : R.drawable.ic_sun, 0, 0, 0);
        ((TextView) view.findViewById(R.id.weather_tip_title))
                .setText(cold ? R.string.weather_tip_cold_title : R.string.weather_tip_hot_title);
        ((TextView) view.findViewById(R.id.weather_tip_body))
                .setText(getString(R.string.weather_tip_body, args.getString(ARG_PIECES)));
        view.findViewById(R.id.weather_tip_add).setOnClickListener(v -> answer(true));
        view.findViewById(R.id.weather_tip_skip).setOnClickListener(v -> answer(false));
    }

    private void answer(boolean add) {
        Bundle result = new Bundle();
        result.putBoolean(RESULT_ADD, add);
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }
}
