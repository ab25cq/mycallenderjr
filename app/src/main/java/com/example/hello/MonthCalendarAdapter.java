package com.example.myhelloworld;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

public class MonthCalendarAdapter extends BaseAdapter {
    private static final int DEFAULT_CELL_HEIGHT_DP = 52;
    private static final int CELL_HORIZONTAL_PADDING_DP = 3;
    private static final int CELL_VERTICAL_PADDING_DP = 3;
    private static final int DAY_TEXT_SIZE_DP = 12;
    private static final int SUMMARY_TEXT_SIZE_DP = 10;
    private static final String COLOR_SELECTED = "#CC0F172A";
    private static final String COLOR_CELL = "#CC000000";
    private static final String COLOR_SATURDAY_CELL = "#D98B2D66";
    private static final String COLOR_SUNDAY_CELL = "#D9B91F4C";
    private static final String COLOR_HOLIDAY_CELL = "#D9B91F4C";
    private static final String COLOR_TODAY_BORDER = "#EF4444";
    private static final int COLOR_CURRENT_MONTH_TEXT = 0xFFFFFFFF;
    private static final int COLOR_OUTSIDE_MONTH_TEXT = 0xFFCBD5E1;

    private final Context context;
    private final List<MonthDayCell> cells;
    private int cellHeightDp = DEFAULT_CELL_HEIGHT_DP;
    private int maxSummaryLines = 3;
    private boolean wrapSummaryText = true;

    public MonthCalendarAdapter(Context context, List<MonthDayCell> cells) {
        this.context = context;
        this.cells = cells;
    }

    public void setDisplayDensity(int cellHeightDp, int maxSummaryLines) {
        this.cellHeightDp = cellHeightDp;
        this.maxSummaryLines = maxSummaryLines;
    }

    public void setWrapSummaryText(boolean wrapSummaryText) {
        this.wrapSummaryText = wrapSummaryText;
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
            itemLayout.setPadding(
                    dp(CELL_HORIZONTAL_PADDING_DP),
                    dp(CELL_VERTICAL_PADDING_DP),
                    dp(CELL_HORIZONTAL_PADDING_DP),
                    dp(CELL_VERTICAL_PADDING_DP)
            );
            itemLayout.setGravity(Gravity.TOP | Gravity.START);
            itemLayout.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    dp(cellHeightDp)
            ));

            dayView = new TextView(context);
            dayView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DAY_TEXT_SIZE_DP);
            dayView.setIncludeFontPadding(false);
            dayView.setTypeface(dayView.getTypeface(), android.graphics.Typeface.BOLD);

            summaryView = new TextView(context);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SUMMARY_TEXT_SIZE_DP);
            summaryView.setIncludeFontPadding(false);
            summaryView.setLineSpacing(0f, 1.0f);
            summaryView.setPadding(0, dp(1), 0, 0);
            summaryView.setMaxLines(maxSummaryLines);
            summaryView.setEllipsize(TextUtils.TruncateAt.END);

            itemLayout.addView(dayView);
            itemLayout.addView(summaryView);
            itemLayout.setTag(new CellViews(dayView, summaryView));
        } else {
            itemLayout = (LinearLayout) convertView;
        }

        CellViews cellViews = (CellViews) itemLayout.getTag();
        MonthDayCell cell = cells.get(position);
        ViewGroup.LayoutParams layoutParams = itemLayout.getLayoutParams();
        int targetHeight = dp(cellHeightDp);
        if (layoutParams != null && layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight;
            itemLayout.setLayoutParams(layoutParams);
        }

        cellViews.dayView.setText(cell.dayLabel);
        cellViews.summaryView.setText(cell.summaryText);
        cellViews.summaryView.setMaxLines(maxSummaryLines);
        cellViews.summaryView.setSingleLine(!wrapSummaryText);
        cellViews.summaryView.setHorizontallyScrolling(!wrapSummaryText);
        cellViews.summaryView.setVisibility(TextUtils.isEmpty(cell.summaryText) ? View.INVISIBLE : View.VISIBLE);

        itemLayout.setBackground(buildCellBackground(cell));

        int textColor = cell.isCurrentMonth ? COLOR_CURRENT_MONTH_TEXT : COLOR_OUTSIDE_MONTH_TEXT;
        cellViews.dayView.setTextColor(textColor);
        cellViews.summaryView.setTextColor(textColor);

        return itemLayout;
    }

    private int dp(int value) {
        if (value == 0) {
            return 0;
        }
        int pixels = Math.round(value * context.getResources().getDisplayMetrics().density);
        return value > 0 ? Math.max(1, pixels) : Math.min(-1, pixels);
    }

    private GradientDrawable buildCellBackground(MonthDayCell cell) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(getCellBackgroundColor(cell)));
        if (cell.isToday) {
            drawable.setStroke(dp(2), Color.parseColor(COLOR_TODAY_BORDER));
        }
        return drawable;
    }

    private String getCellBackgroundColor(MonthDayCell cell) {
        if (cell.isSelected) {
            return COLOR_SELECTED;
        }
        if (cell.isHoliday || cell.isSunday) {
            return COLOR_HOLIDAY_CELL;
        }
        if (cell.isSaturday) {
            return COLOR_SATURDAY_CELL;
        }
        return COLOR_CELL;
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
    final CharSequence summaryText;
    final boolean isSunday;
    final boolean isSaturday;
    final boolean isHoliday;
    final boolean isCurrentMonth;
    final boolean isToday;
    final boolean isSelected;

    MonthDayCell(
            long dayStartMillis,
            String dayLabel,
            CharSequence summaryText,
            boolean isSunday,
            boolean isSaturday,
            boolean isHoliday,
            boolean isCurrentMonth,
            boolean isToday,
            boolean isSelected
    ) {
        this.dayStartMillis = dayStartMillis;
        this.dayLabel = dayLabel;
        this.summaryText = summaryText;
        this.isSunday = isSunday;
        this.isSaturday = isSaturday;
        this.isHoliday = isHoliday;
        this.isCurrentMonth = isCurrentMonth;
        this.isToday = isToday;
        this.isSelected = isSelected;
    }
}
