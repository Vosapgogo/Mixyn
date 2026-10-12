package com.mixyn.app.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.mixyn.app.R;
import com.mixyn.app.api.ApiClient;
import com.mixyn.app.api.dto.OutfitDto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The cells of one month, seven to a row, weeks starting on Monday. Before the 1st and after the last
 * day the rows are filled up with blank cells, so the month is always a full rectangle. The weeks
 * share the height of the grid between them: the whole month is on screen, whether it has four weeks,
 * five or six.
 */
public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.Holder> {

    public static final int DAYS_IN_WEEK = 7;
    private static final DateTimeFormatter SPOKEN_DAY = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    public interface OnOutfitClick {
        void onClick(OutfitDto outfit);
    }

    /** One cell: a day of the month, or null for a blank cell before or after it. */
    private final List<LocalDate> cells = new ArrayList<>();
    /** How many blank cells come before the 1st. */
    private int blanksBefore;
    /** The outfit worn on a day, for the days that have one. */
    private Map<LocalDate, OutfitDto> worn = new HashMap<>();
    private final OnOutfitClick onOutfitClick;
    @Nullable
    private RecyclerView grid;

    public CalendarAdapter(OnOutfitClick onOutfitClick) {
        this.onOutfitClick = onOutfitClick;
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        grid = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        grid = null;
    }

    /** The grid's height split between the weeks of the month, but never less than the minimum. */
    private int cellHeight(View cell) {
        int minimum = cell.getResources().getDimensionPixelSize(R.dimen.calendar_day_min_height);
        int weeks = cells.size() / DAYS_IN_WEEK;
        return grid == null || weeks == 0 ? minimum : Math.max(minimum, grid.getHeight() / weeks);
    }

    public void show(YearMonth month, Map<LocalDate, OutfitDto> wornInMonth) {
        cells.clear();
        blanksBefore = month.atDay(1).getDayOfWeek().getValue() - 1; // Monday is 1
        for (int i = 0; i < blanksBefore; i++) cells.add(null);
        for (int day = 1; day <= month.lengthOfMonth(); day++) cells.add(month.atDay(day));
        while (cells.size() % DAYS_IN_WEEK != 0) cells.add(null);
        worn = wornInMonth;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_calendar_day, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocalDate day = cells.get(position);
        OutfitDto outfit = day == null ? null : worn.get(day);
        LocalDate today = LocalDate.now();
        ViewGroup.LayoutParams size = holder.cell.getLayoutParams();
        size.height = cellHeight(holder.cell);
        holder.cell.setLayoutParams(size);

        if (day == null) {
            holder.cell.setBackgroundResource(position < blanksBefore ? R.color.mocha_light : R.color.line);
        } else {
            holder.cell.setBackgroundResource(day.equals(today) ? R.drawable.bg_calendar_today : R.drawable.bg_calendar_day);
        }
        holder.number.setText(day == null ? "" : String.valueOf(day.getDayOfMonth()));
        // Days that haven't come yet are quieter; today stands out.
        int numberColor = day == null || day.isAfter(today) ? R.color.ink_muted
                : day.equals(today) ? R.color.mocha : R.color.ink;
        holder.number.setTextColor(ContextCompat.getColor(holder.number.getContext(), numberColor));

        bindOutfit(holder, day, outfit);
    }

    private void bindOutfit(Holder holder, @Nullable LocalDate day, @Nullable OutfitDto outfit) {
        if (outfit == null) {
            Glide.with(holder.outfit).clear(holder.outfit);
            holder.outfit.setContentDescription(null);
            holder.cell.setOnClickListener(null);
            holder.cell.setClickable(false);
            return;
        }
        Glide.with(holder.outfit).load(ApiClient.imageUrl(outfit.imageUrl)).into(holder.outfit);
        holder.outfit.setContentDescription(holder.outfit.getContext()
                .getString(R.string.calendar_day_outfit, day.format(SPOKEN_DAY)));
        holder.cell.setOnClickListener(v -> onOutfitClick.onClick(outfit));
    }

    @Override
    public int getItemCount() {
        return cells.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View cell;
        final TextView number;
        final ImageView outfit;

        Holder(View view) {
            super(view);
            cell = view.findViewById(R.id.day_cell);
            number = view.findViewById(R.id.day_number);
            outfit = view.findViewById(R.id.day_outfit);
        }
    }
}
