package com.example.myhelloworld;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MonthCalendarAdapter extends BaseAdapter {
    private final Context context;
    private final List<MonthDayCell> cells;

    public MonthCalendarAdapter(Context context, List<MonthDayCell> cells) {
        this.context = context;
        this.cells = cells;
    }

    @Override
    public int getCount() {
        return cells.size();
    }

    @Override
    public Object getItem(int position) {
        return cells.get(position);
    }

    @Override
    public long getItemId(int position) {
        return cells.get(position).dayStartMillis;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LinearLayout itemLayout;
        TextView dayView;
        TextView summaryView;

        if (convertView == null) {
            itemLayout = new LinearLayout(context);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(dp(6), dp(6), dp(6), dp(6));
            itemLayout.setGravity(Gravity.TOP | Gravity.START);
            itemLayout.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    dp(72)
            ));

            dayView = new TextView(context);
            dayView.setTextSize(13);
            dayView.setTypeface(dayView.getTypeface(), android.graphics.Typeface.BOLD);

            summaryView = new TextView(context);
            summaryView.setTextSize(8);
            summaryView.setPadding(0, dp(2), 0, 0);
            summaryView.setMaxLines(3);
            summaryView.setSingleLine(false);

            itemLayout.addView(dayView);
            itemLayout.addView(summaryView);
            itemLayout.setTag(new CellViews(dayView, summaryView));
        } else {
            itemLayout = (LinearLayout) convertView;
        }

        CellViews cellViews = (CellViews) itemLayout.getTag();
        MonthDayCell cell = cells.get(position);

        cellViews.dayView.setText(cell.dayLabel);
        cellViews.summaryView.setText(cell.summaryText);
        cellViews.summaryView.setVisibility(TextUtils.isEmpty(cell.summaryText) ? View.INVISIBLE : View.VISIBLE);

        if (cell.isSelected) {
            itemLayout.setBackgroundColor(Color.parseColor("#DCEBFF"));
        } else if (cell.isCurrentMonth) {
            itemLayout.setBackgroundColor(Color.WHITE);
        } else {
            itemLayout.setBackgroundColor(Color.parseColor("#F1F5F9"));
        }

        if (cell.isToday) {
            cellViews.dayView.setTextColor(Color.parseColor("#0F4C81"));
        } else if (cell.isCurrentMonth) {
            cellViews.dayView.setTextColor(Color.parseColor("#111827"));
        } else {
            cellViews.dayView.setTextColor(Color.parseColor("#94A3B8"));
        }

        cellViews.summaryView.setTextColor(cell.isCurrentMonth
                ? Color.parseColor("#334155")
                : Color.parseColor("#94A3B8"));

        return itemLayout;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private static final class CellViews {
        final TextView dayView;
        final TextView summaryView;

        CellViews(TextView dayView, TextView summaryView) {
            this.dayView = dayView;
            this.summaryView = summaryView;
        }
    }
}

final class MonthDayCell {
    final long dayStartMillis;
    final String dayLabel;
    final String summaryText;
    final boolean isCurrentMonth;
    final boolean isToday;
    final boolean isSelected;

    MonthDayCell(
            long dayStartMillis,
            String dayLabel,
            String summaryText,
            boolean isCurrentMonth,
            boolean isToday,
            boolean isSelected
    ) {
        this.dayStartMillis = dayStartMillis;
        this.dayLabel = dayLabel;
        this.summaryText = summaryText;
        this.isCurrentMonth = isCurrentMonth;
        this.isToday = isToday;
        this.isSelected = isSelected;
    }
}
