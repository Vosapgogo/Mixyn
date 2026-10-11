package com.mixyn.app.ui.canvas;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Offers what the weather calls for and the outfit doesn't have yet: a jacket, beanie, scarf and gloves
 * when it is cool or cold, a cap, hat or headscarf in the heat. Every piece on offer is a card, ticked
 * to begin with; the user unticks what they don't want. The canvas decides what to offer; this sheet
 * only asks. The answer comes back through the Fragment Result API: RESULT_ADD is true for "Add to the
 * outfit" (with the ticked pieces' keys in RESULT_KEYS), false for "Not now"; swiping the sheet away is
 * no answer at all.
 */
public class WeatherTipSheet extends BottomSheetDialogFragment {

    /** One piece on offer: a key the canvas understands, its kind ("Scarf") and the user's own piece. */
    public static class Piece implements Serializable {
        public final String key;
        public final String kind;
        public final String name;
        public final String imageUrl;

        public Piece(String key, String kind, String name, String imageUrl) {
            this.key = key;
            this.kind = kind;
            this.name = name;
            this.imageUrl = imageUrl;
        }
    }

    public static final String REQUEST_KEY = "weather_tip";
    public static final String RESULT_ADD = "add";
    public static final String RESULT_KEYS = "keys";
    private static final String ARG_WEATHER = "weather";
    private static final String ARG_COLD = "cold";
    private static final String ARG_PIECES = "pieces";
    private static final int CARDS_PER_ROW = 2;
    private static final float DISABLED_ALPHA = 0.4f;

    private final ArrayList<String> chosen = new ArrayList<>();
    private View add;

    /**
     * @param weather the line from the weather chip, e.g. "4° · cold"
     * @param cold    true for the cold-weather tip, false for the hot-weather one
     */
    public static WeatherTipSheet newInstance(String weather, boolean cold, ArrayList<Piece> pieces) {
        Bundle args = new Bundle();
        args.putString(ARG_WEATHER, weather);
        args.putBoolean(ARG_COLD, cold);
        args.putSerializable(ARG_PIECES, pieces);
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
    @SuppressWarnings("unchecked")
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        boolean cold = args.getBoolean(ARG_COLD);
        TextView weather = view.findViewById(R.id.weather_tip_weather);
        weather.setText(args.getString(ARG_WEATHER));
        weather.setCompoundDrawablesRelativeWithIntrinsicBounds(cold ? R.drawable.ic_cloud : R.drawable.ic_sun, 0, 0, 0);
        ((TextView) view.findViewById(R.id.weather_tip_title))
                .setText(cold ? R.string.weather_tip_cold_title : R.string.weather_tip_hot_title);
        ((TextView) view.findViewById(R.id.weather_tip_body))
                .setText(cold ? R.string.weather_tip_cold_body : R.string.weather_tip_hot_body);

        add = view.findViewById(R.id.weather_tip_add);
        add.setOnClickListener(v -> answer(true));
        view.findViewById(R.id.weather_tip_skip).setOnClickListener(v -> answer(false));
        showCards(view.findViewById(R.id.weather_tip_pieces), (ArrayList<Piece>) args.getSerializable(ARG_PIECES));
    }

    /** The sheet is taller than the default half-open height, so it opens fully. */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog dialog) {
            dialog.getBehavior().setSkipCollapsed(true);
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    /** Two cards per row; a card alone in its row takes the whole width, like the cap in the design. */
    private void showCards(LinearLayout rows, ArrayList<Piece> pieces) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int gap = getResources().getDimensionPixelSize(R.dimen.canvas_control_gap);
        LinearLayout row = null;
        for (Piece piece : pieces) {
            if (row == null || row.getChildCount() == CARDS_PER_ROW) {
                row = new LinearLayout(requireContext());
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = gap;
                rows.addView(row, rowParams);
            }
            View card = inflater.inflate(R.layout.item_tip_piece, row, false);
            if (row.getChildCount() > 0) ((LinearLayout.LayoutParams) card.getLayoutParams()).setMarginStart(gap);
            ((TextView) card.findViewById(R.id.tip_piece_kind)).setText(piece.kind);
            ((TextView) card.findViewById(R.id.tip_piece_name)).setText(piece.name);
            ImageView image = card.findViewById(R.id.tip_piece_image);
            Glide.with(image).load(ApiClient.imageUrl(piece.imageUrl)).into(image);
            chosen.add(piece.key);
            card.setSelected(true);
            card.setOnClickListener(v -> {
                card.setSelected(!card.isSelected());
                if (card.isSelected()) chosen.add(piece.key);
                else chosen.remove(piece.key);
                // Nothing ticked = nothing to add; "Not now" is the way out.
                add.setEnabled(!chosen.isEmpty());
                add.setAlpha(chosen.isEmpty() ? DISABLED_ALPHA : 1f);
            });
            row.addView(card);
        }
    }

    private void answer(boolean add) {
        Bundle result = new Bundle();
        result.putBoolean(RESULT_ADD, add);
        result.putStringArrayList(RESULT_KEYS, new ArrayList<>(chosen));
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }
}
