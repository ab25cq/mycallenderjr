package com.example.myhelloworld;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public final class CalendarRepository {
    private static final String TAG = "MyCalendarRepo";
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long INSTANCE_QUERY_PADDING_MILLIS = 31L * DAY_IN_MILLIS;

    private CalendarRepository() {
    }

    public static List<CalendarInfo> getWritableCalendars(Context context) {
        List<CalendarInfo> calendars = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };

        String selection = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = {
                String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
        };
        String orderBy = CalendarContract.Calendars.IS_PRIMARY + " DESC, "
                + CalendarContract.Calendars.ACCOUNT_NAME + " ASC, "
                + CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC";

        Cursor cursor = resolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                orderBy
        );

        if (cursor == null) {
            return calendars;
        }

        try {
            int idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID);
            int displayNameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME);
            int accountNameIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME);
            int accountTypeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE);

            while (cursor.moveToNext()) {
                calendars.add(new CalendarInfo(
                        cursor.getLong(idIndex),
                        cursor.getString(displayNameIndex),
                        cursor.getString(accountNameIndex),
                        cursor.getString(accountTypeIndex)
                ));
            }
        } finally {
            cursor.close();
        }

        return calendars;
    }

    public static List<CalendarEvent> getEventsForDay(Context context, long calendarId, long dayStartMillis) {
        long dayEndMillis = dayStartMillis + 24L * 60L * 60L * 1000L;
        List<CalendarEvent> events = getEventsForRange(context, calendarId, dayStartMillis, dayEndMillis);
        Log.d(TAG, "getEventsForDay calendarId=" + calendarId
                + " dayStart=" + dayStartMillis
                + " count=" + events.size()
                + " events=" + summarizeEvents(events));
        return events;
    }

    public static List<CalendarEvent> getEventsForRange(
            Context context,
            long calendarId,
            long rangeStartMillis,
            long rangeEndMillis
    ) {
        List<CalendarEvent> events = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
                CalendarContract.Instances._ID,
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
        };

        String selection = CalendarContract.Instances.CALENDAR_ID + " = ?";
        String[] selectionArgs = {
                String.valueOf(calendarId)
        };
        String sortOrder = CalendarContract.Instances.ALL_DAY + " DESC, "
                + CalendarContract.Instances.BEGIN + " ASC";

        long queryStartMillis = Math.max(0L, rangeStartMillis - INSTANCE_QUERY_PADDING_MILLIS);
        long queryEndMillis = rangeEndMillis + INSTANCE_QUERY_PADDING_MILLIS;
        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, queryStartMillis);
        ContentUris.appendId(builder, queryEndMillis);

        Cursor cursor = resolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                sortOrder
        );

        if (cursor == null) {
            return events;
        }

        try {
            int idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID);
            int eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE);
            int descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION);
            int startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END);
            int allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY);
            int calendarIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID);

            while (cursor.moveToNext()) {
                long startMillis = cursor.getLong(startIndex);
                long endMillis = cursor.isNull(endIndex) ? startMillis : cursor.getLong(endIndex);
                if (endMillis == startMillis) {
                    endMillis = startMillis + 60L * 60L * 1000L;
                }
                if (startMillis >= rangeEndMillis || endMillis <= rangeStartMillis) {
                    continue;
                }

                String title = cursor.getString(titleIndex);
                String description = cursor.getString(descriptionIndex);

                events.add(new CalendarEvent(
                        cursor.getLong(idIndex),
                        cursor.getLong(eventIdIndex),
                        cursor.getLong(calendarIdIndex),
                        TextUtils.isEmpty(title) ? "(無題)" : title,
                        description == null ? "" : description,
                        startMillis,
                        endMillis,
                        cursor.getInt(allDayIndex) == 1
                ));
            }
        } finally {
            cursor.close();
        }

        return events;
    }

    private static String summarizeEvents(List<CalendarEvent> events) {
        if (events.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(events.size(), 8);
        for (int i = 0; i < limit; i++) {
            CalendarEvent event = events.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("{id=").append(event.id)
                    .append(",eventId=").append(event.eventId)
                    .append(",title=").append(event.title)
                    .append(",start=").append(event.startMillis)
                    .append(",end=").append(event.endMillis)
                    .append("}");
        }
        if (events.size() > limit) {
            builder.append(", ... total=").append(events.size());
        }
        builder.append(']');
        return builder.toString();
    }

    public static long insertEvent(
            Context context,
            long calendarId,
            String title,
            String description,
            long startMillis,
            long endMillis,
            boolean allDay
    ) {
        ContentValues values = buildEventValues(calendarId, title, description, startMillis, endMillis, allDay);
        Uri uri = context.getContentResolver().insert(CalendarContract.Events.CONTENT_URI, values);
        return uri == null ? -1L : ContentUris.parseId(uri);
    }

    public static boolean updateEvent(
            Context context,
            long eventId,
            long calendarId,
            String title,
            String description,
            long startMillis,
            long endMillis,
            boolean allDay
    ) {
        ContentValues values = buildEventValues(calendarId, title, description, startMillis, endMillis, allDay);
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return context.getContentResolver().update(uri, values, null, null) > 0;
    }

    public static boolean deleteEvent(Context context, CalendarEvent event, CalendarInfo calendarInfo) {
        try {
            Uri eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
            int deletedRows = context.getContentResolver().delete(eventUri, null, null);
            if (deletedRows > 0) {
                return true;
            }

            if (calendarInfo == null || TextUtils.isEmpty(calendarInfo.accountName) || TextUtils.isEmpty(calendarInfo.accountType)) {
                Log.w(TAG, "deleteEvent affected no rows for eventId=" + event.eventId + " calendarId=" + event.calendarId);
                return false;
            }

            try {
                Uri syncAdapterUri = CalendarContract.Events.CONTENT_URI.buildUpon()
                        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, calendarInfo.accountName)
                        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, calendarInfo.accountType)
                        .build();
                Uri syncAdapterEventUri = ContentUris.withAppendedId(syncAdapterUri, event.eventId);
                int syncDeletedRows = context.getContentResolver().delete(syncAdapterEventUri, null, null);
                if (syncDeletedRows > 0) {
                    return true;
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "sync-adapter delete failed for eventId=" + event.eventId, e);
            }

            Log.w(TAG, "deleteEvent affected no rows after fallback for eventId=" + event.eventId
                    + " calendarId=" + event.calendarId);
            return false;
        } catch (RuntimeException e) {
            Log.e(TAG, "deleteEvent failed for eventId=" + event.eventId + " calendarId=" + event.calendarId, e);
            return false;
        }
    }

    private static ContentValues buildEventValues(
            long calendarId,
            String title,
            String description,
            long startMillis,
            long endMillis,
            boolean allDay
    ) {
        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        values.put(CalendarContract.Events.TITLE, title);
        values.put(CalendarContract.Events.DESCRIPTION, description);
        values.put(CalendarContract.Events.DTSTART, startMillis);
        values.put(CalendarContract.Events.DTEND, endMillis);
        values.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, allDay ? "UTC" : TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.HAS_ALARM, 0);
        return values;
    }

    public static final class CalendarInfo {
        public final long id;
        public final String displayName;
        public final String accountName;
        public final String accountType;

        CalendarInfo(long id, String displayName, String accountName, String accountType) {
            this.id = id;
            this.displayName = displayName == null ? "(名称なし)" : displayName;
            this.accountName = accountName == null ? "(アカウントなし)" : accountName;
            this.accountType = accountType == null ? "" : accountType;
        }

        public boolean isGoogleCalendar() {
            return "com.google".equals(accountType);
        }
    }

    public static final class CalendarEvent {
        public final long id;
        public final long eventId;
        public final long calendarId;
        public final String title;
        public final String description;
        public final long startMillis;
        public final long endMillis;
        public final boolean allDay;

        CalendarEvent(
                long id,
                long eventId,
                long calendarId,
                String title,
                String description,
                long startMillis,
                long endMillis,
                boolean allDay
        ) {
            this.id = id;
            this.eventId = eventId;
            this.calendarId = calendarId;
            this.title = title;
            this.description = description;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.allDay = allDay;
        }
    }
}
