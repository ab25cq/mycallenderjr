package com.example.myhelloworld;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public final class CalendarRepository {
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
        return getEventsForRange(context, calendarId, dayStartMillis, dayEndMillis);
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
                CalendarContract.Events._ID,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY
        };

        String selection = CalendarContract.Events.CALENDAR_ID + " = ? AND "
                + CalendarContract.Events.DELETED + " = 0 AND "
                + CalendarContract.Events.DTSTART + " < ? AND "
                + CalendarContract.Events.DTEND + " > ?";
        String[] selectionArgs = {
                String.valueOf(calendarId),
                String.valueOf(rangeEndMillis),
                String.valueOf(rangeStartMillis)
        };
        String sortOrder = CalendarContract.Events.ALL_DAY + " DESC, "
                + CalendarContract.Events.DTSTART + " ASC";

        Cursor cursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        );

        if (cursor == null) {
            return events;
        }

        try {
            int idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE);
            int descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION);
            int startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND);
            int allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY);

            while (cursor.moveToNext()) {
                long startMillis = cursor.getLong(startIndex);
                long endMillis = cursor.isNull(endIndex) ? startMillis : cursor.getLong(endIndex);
                if (endMillis == startMillis) {
                    endMillis = startMillis + 60L * 60L * 1000L;
                }

                String title = cursor.getString(titleIndex);
                String description = cursor.getString(descriptionIndex);

                events.add(new CalendarEvent(
                        cursor.getLong(idIndex),
                        calendarId,
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

    public static boolean deleteEvent(Context context, long eventId) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        return context.getContentResolver().delete(uri, null, null) > 0;
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
        public final long calendarId;
        public final String title;
        public final String description;
        public final long startMillis;
        public final long endMillis;
        public final boolean allDay;

        CalendarEvent(
                long id,
                long calendarId,
                String title,
                String description,
                long startMillis,
                long endMillis,
                boolean allDay
        ) {
            this.id = id;
            this.calendarId = calendarId;
            this.title = title;
            this.description = description;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.allDay = allDay;
        }
    }
}
