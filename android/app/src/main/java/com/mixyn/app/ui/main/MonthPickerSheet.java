package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mixyn.app.R;

import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * The quick way around the calendar: pick a year with the arrows, then tap one of its months. Months
 * that haven't come yet can't be chosen, and the arrows don't go past this year. The chosen month comes
 * back through the Fragment Result API as "2026-10".
 */
public class MonthPickerSheet extends BottomSheetDialogFragment {

    public static final String REQUEST_KEY = "month_picker";
    public static final String RESULT_MONTH = "month";
    private static final String ARG_SHOWN = "shown";
    private static final String STATE_YEAR = "year";
    private static final int COLUMNS = 4;
    /** What can't be tapped (a month or a year that hasn't come yet) is faded to this. */
    private static final float DISABLED_ALPHA = 0.3f;

    /** The month the calendar is on: it is the one highlighted. */
    private YearMonth shown;
    private int year;
    private TextView yearTitle;
    private View nextYear;
    private LinearLayout months;

    public static MonthPickerSheet newInstance(YearMonth shown) {
        Bundle args = new Bundle();
        args.putString(ARG_SHOWN, shown.toString());
        MonthPickerSheet sheet = new MonthPickerSheet();
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_month_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        shown = YearMonth.parse(requireArguments().getString(ARG_SHOWN));
        year = savedInstanceState != null ? savedInstanceState.getInt(STATE_YEAR) : shown.getYear();
        yearTitle = view.findViewById(R.id.picker_year);
        nextYear = view.findViewById(R.id.picker_next_year);
        months = view.findViewById(R.id.picker_months);
        view.findViewById(R.id.picker_previous_year).setOnClickListener(v -> showYear(year - 1));
        nextYear.setOnClickListener(v -> showYear(year + 1));
        showYear(year);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_YEAR, year);
    }

    private void showYear(int wanted) {
        year = wanted;
        YearMonth now = YearMonth.now();
        yearTitle.setText(String.valueOf(year));
        boolean hasNext = year < now.getYear();
        nextYear.setEnabled(hasNext);
        nextYear.setAlpha(hasNext ? 1f : DISABLED_ALPHA);

        months.removeAllViews();
        int gap = getResources().getDimensionPixelSize(R.dimen.space_xxs);
        int height = getResources().getDimensionPixelSize(R.dimen.chip_height);
        LinearLayout row = null;
        for (Month name : Month.values()) {
            if (row == null || row.getChildCount() == COLUMNS) {
                row = new LinearLayout(requireContext());
                months.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            YearMonth month = YearMonth.of(year, name);
            boolean reachable = !month.isAfter(now);
            // The same chip as in the choice sheets, one line of them filling the row.
            TextView chip = new TextView(requireContext(), null, 0, R.style.Widget_Mixyn_Chip);
            chip.setText(name.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            chip.setGravity(Gravity.CENTER);
            chip.setSelected(month.equals(shown));
            chip.setAlpha(reachable ? 1f : DISABLED_ALPHA);
            if (reachable) chip.setOnClickListener(v -> choose(month));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height, 1f);
            params.setMargins(gap, gap, gap, gap);
            row.addView(chip, params);
        }
    }

    private void choose(YearMonth month) {
        Bundle result = new Bundle();
        result.putString(RESULT_MONTH, month.toString());
        getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
        dismiss();
    }
}
