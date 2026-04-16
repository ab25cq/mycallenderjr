package com.example.myhelloworld;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.List;

public class ScheduleListAdapter extends BaseAdapter {
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_MESSAGE = 2;
    private static final int ITEM_KIND_SECTION = 0;
    private static final int ITEM_KIND_EVENT = 1;
    private static final int ITEM_KIND_TODO = 2;
    private static final int ITEM_KIND_MESSAGE = 3;

    private final Context context;
    private final List<ScheduleListItem> items;
    private long selectedEventId = -1L;

    public ScheduleListAdapter(Context context, List<ScheduleListItem> items) {
        this.context = context;
        this.items = items;
    }

    public void setSelectedEventId(long selectedEventId) {
        this.selectedEventId = selectedEventId;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        ScheduleListItem item = items.get(position);
        if (item.event != null) {
            return item.event.id;
        }
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public int getItemViewType(int position) {
        ScheduleListItem item = items.get(position);
        if (item.kind == ITEM_KIND_SECTION) {
            return TYPE_SECTION;
        }
        if (item.kind == ITEM_KIND_MESSAGE) {
            return TYPE_MESSAGE;
        }
        return TYPE_ROW;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        int viewType = getItemViewType(position);
        if (viewType == TYPE_SECTION) {
            return bindSectionView(position, convertView);
        }
        if (viewType == TYPE_MESSAGE) {
            return bindMessageView(position, convertView);
        }
        return bindRowView(position, convertView);
    }

    private View bindSectionView(int position, View convertView) {
        TextView sectionView;
        if (convertView instanceof TextView) {
            sectionView = (TextView) convertView;
        } else {
            sectionView = new TextView(context);
            sectionView.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT
            ));
            sectionView.setTextSize(11);
            sectionView.setTypeface(sectionView.getTypeface(), android.graphics.Typeface.BOLD);
            sectionView.setTextColor(Color.parseColor("#475569"));
            sectionView.setPadding(dp(8), dp(6), dp(8), dp(4));
        }
        sectionView.setText(items.get(position).title);
        return sectionView;
    }

    private View bindMessageView(int position, View convertView) {
        TextView messageView;
        if (convertView instanceof TextView) {
            messageView = (TextView) convertView;
        } else {
            messageView = new TextView(context);
            messageView.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT
            ));
            messageView.setTextSize(11);
            messageView.setPadding(dp(14), dp(8), dp(14), dp(8));
            messageView.setTextColor(Color.parseColor("#64748B"));
        }
        messageView.setText(items.get(position).title);
        return messageView;
    }

    private View bindRowView(int position, View convertView) {
        LinearLayout itemLayout;
        TextView titleView;
        TextView detailView;

        if (convertView == null || !(convertView instanceof LinearLayout)) {
            itemLayout = new LinearLayout(context);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(dp(14), dp(8), dp(14), dp(8));
            itemLayout.setLayoutParams(new AbsListView.LayoutParams(
                    AbsListView.LayoutParams.MATCH_PARENT,
                    AbsListView.LayoutParams.WRAP_CONTENT
            ));

            titleView = new TextView(context);
            titleView.setTextSize(13);
            titleView.setTextColor(Color.parseColor("#16324F"));
            titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);

            detailView = new TextView(context);
            detailView.setTextSize(10);
            detailView.setTextColor(Color.parseColor("#52667A"));
            detailView.setPadding(0, dp(2), 0, 0);

            itemLayout.addView(titleView);
            itemLayout.addView(detailView);
            itemLayout.setTag(new RowViews(titleView, detailView));
        } else {
            itemLayout = (LinearLayout) convertView;
        }

        RowViews rowViews = (RowViews) itemLayout.getTag();
        ScheduleListItem item = items.get(position);
        rowViews.titleView.setText(item.title);
        rowViews.detailView.setText(item.detail);
        rowViews.detailView.setVisibility(TextUtils.isEmpty(item.detail) ? View.GONE : View.VISIBLE);

        if (item.kind == ITEM_KIND_EVENT) {
            itemLayout.setBackgroundColor(item.event != null && item.event.id == selectedEventId
                    ? Color.parseColor("#DCEBFF")
                    : Color.WHITE);
        } else {
            itemLayout.setBackgroundColor(Color.parseColor("#F8FAFC"));
        }

        return itemLayout;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    public static final class ScheduleListItem {
        final int kind;
        final String title;
        final String detail;
        final CalendarRepository.CalendarEvent event;
        final LocalTodoRepository.LocalTodo todo;

        private ScheduleListItem(
                int kind,
                String title,
                String detail,
                CalendarRepository.CalendarEvent event,
                LocalTodoRepository.LocalTodo todo
        ) {
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.event = event;
            this.todo = todo;
        }

        static ScheduleListItem section(String title) {
            return new ScheduleListItem(ITEM_KIND_SECTION, title, "", null, null);
        }

        static ScheduleListItem message(String message) {
            return new ScheduleListItem(ITEM_KIND_MESSAGE, message, "", null, null);
        }

        static ScheduleListItem event(Context context, CalendarRepository.CalendarEvent event) {
            String detail;
            if (event.allDay) {
                detail = "終日";
            } else {
                java.text.DateFormat timeFormat = DateFormat.getTimeFormat(context);
                detail = timeFormat.format(new Date(event.startMillis))
                        + " - "
                        + timeFormat.format(new Date(event.endMillis));
            }
            if (!TextUtils.isEmpty(event.description)) {
                detail = detail + "\n" + event.description;
            }
            return new ScheduleListItem(ITEM_KIND_EVENT, event.title, detail, event, null);
        }

        static ScheduleListItem todo(LocalTodoRepository.LocalTodo todo) {
            return new ScheduleListItem(ITEM_KIND_TODO, todo.title, "", null, todo);
        }

        boolean isEvent() {
            return kind == ITEM_KIND_EVENT && event != null;
        }

        boolean isTodo() {
            return kind == ITEM_KIND_TODO && todo != null;
        }
    }

    private static final class RowViews {
        final TextView titleView;
        final TextView detailView;

        RowViews(TextView titleView, TextView detailView) {
            this.titleView = titleView;
            this.detailView = detailView;
        }
    }
}
