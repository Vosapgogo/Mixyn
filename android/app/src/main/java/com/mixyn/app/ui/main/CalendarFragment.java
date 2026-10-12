package com.mixyn.app.ui.main;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mixyn.app.R;
import com.mixyn.app.api.ApiCallback;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.CalendarDayDto;
import com.mixyn.app.api.dto.OutfitDto;
import com.mixyn.app.session.SessionStore;
import com.mixyn.app.ui.outfit.OutfitActivity;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Calendar tab: a month of days, each showing the outfit that was worn on it ("Wear it today" on an
 * outfit's card is what puts it there). The arrows go to earlier months and back, never past this month.
 * Tapping a day with an outfit opens that outfit's card.
 */
public class CalendarFragment extends Fragment {

    private static final String STATE_MONTH = "month";
    private static final DateTimeFormatter MONTH_AND_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    /** The arrow that can't be used (there is no month after this one) is faded to this. */
    private static final float DISABLED_ALPHA = 0.3f;

    private YearMonth month = YearMonth.now();
    private CalendarAdapter adapter;
    private TextView title;
    private View next;

    public CalendarFragment() {
        super(R.layout.fragment_calendar);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) month = YearMonth.parse(savedInstanceState.getString(STATE_MONTH));
        title = view.findViewById(R.id.calendar_month);
        next = view.findViewById(R.id.calendar_next);
        view.findViewById(R.id.calendar_previous).setOnClickListener(v -> show(month.minusMonths(1)));
        next.setOnClickListener(v -> show(month.plusMonths(1)));

        LinearLayout weekdays = view.findViewById(R.id.calendar_weekdays);
        for (String letter : getResources().getStringArray(R.array.weekday_letters)) {
            TextView day = new TextView(requireContext());
            TextViewCompat.setTextAppearance(day, R.style.TextAppearance_Mixyn_Label);
            day.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_muted));
            day.setGravity(Gravity.CENTER);
            day.setText(letter);
            weekdays.addView(day, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }

        adapter = new CalendarAdapter(outfit -> OutfitActivity.start(requireContext(), outfit));
        RecyclerView grid = view.findViewById(R.id.calendar_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), CalendarAdapter.DAYS_IN_WEEK));
        grid.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        show(month); // also picks up an outfit worn, or deleted, a moment ago
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_MONTH, month.toString());
    }

    /** Draws the month right away, empty, and fills in the outfits when the backend has answered. */
    private void show(YearMonth wanted) {
        boolean sameMonth = wanted.equals(month);
        month = wanted;
        title.setText(month.format(MONTH_AND_YEAR));
        boolean hasNext = month.isBefore(YearMonth.now());
        next.setEnabled(hasNext);
        next.setAlpha(hasNext ? 1f : DISABLED_ALPHA);
        // Coming back to the same month keeps its outfits on screen until the fresh ones arrive.
        if (!sameMonth || adapter.getItemCount() == 0) adapter.show(month, new HashMap<>());

        long userId = new SessionStore(requireContext()).getUserId();
        ApiClient.get(requireContext())
                .calendar(userId, month.atDay(1).toString(), month.atEndOfMonth().toString())
                .enqueue(new ApiCallback<>(requireActivity()) {
                    @Override
                    protected void onSuccess(@Nullable List<CalendarDayDto> days) {
                        // The answer may come after the user has moved on to another month.
                        if (getView() == null || days == null || !wanted.equals(month)) return;
                        Map<LocalDate, OutfitDto> worn = new HashMap<>();
                        for (CalendarDayDto day : days) worn.put(LocalDate.parse(day.date), day.outfit);
                        adapter.show(wanted, worn);
                    }

                    @Override
                    protected void onError(@Nullable String code, @NonNull String message) {
                        if (getView() == null) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
