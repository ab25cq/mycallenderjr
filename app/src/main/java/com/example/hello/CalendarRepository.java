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
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public final class CalendarRepository {
    private static final String TAG = "MyCalendarRepo";
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long INSTANCE_QUERY_PADDING_MILLIS = 31L * DAY_IN_MILLIS;
    private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone("UTC");

    private CalendarRepository() {
    }

    public static List<CalendarInfo> getAllCalendars(Context context) {
        List<CalendarInfo> calendars = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars._SYNC_ID,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };

        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = {
                String.valueOf(CalendarContract.Calendars.CAL_ACCESS_READ)
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
            int ownerAccountIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT);
            int accountTypeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE);
            int syncIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._SYNC_ID);
            int accessLevelIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL);

            while (cursor.moveToNext()) {
                calendars.add(new CalendarInfo(
                        cursor.getLong(idIndex),
                        cursor.getString(displayNameIndex),
                        cursor.getString(accountNameIndex),
                        cursor.getString(ownerAccountIndex),
                        cursor.getString(accountTypeIndex),
                        cursor.getString(syncIdIndex),
                        cursor.getInt(accessLevelIndex)
                ));
            }
        } finally {
            cursor.close();
        }

        return calendars;
    }

    public static List<CalendarInfo> getWritableCalendars(Context context) {
        List<CalendarInfo> calendars = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars._SYNC_ID,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };

        String selection = CalendarContract.Calendars.VISIBLE + " = 1 AND "
                + CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ?";
        String[] selectionArgs = {
                String.valueOf(CalendarContract.Calendars.CAL_ACCESS_READ)
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
            int ownerAccountIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.OWNER_ACCOUNT);
            int accountTypeIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE);
            int syncIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._SYNC_ID);
            int accessLevelIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL);

            while (cursor.moveToNext()) {
                calendars.add(new CalendarInfo(
                        cursor.getLong(idIndex),
                        cursor.getString(displayNameIndex),
                        cursor.getString(accountNameIndex),
                        cursor.getString(ownerAccountIndex),
                        cursor.getString(accountTypeIndex),
                        cursor.getString(syncIdIndex),
                        cursor.getInt(accessLevelIndex)
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
                boolean allDay = cursor.getInt(allDayIndex) == 1;
                if (endMillis == startMillis) {
                    endMillis = startMillis + (allDay ? DAY_IN_MILLIS : 60L * 60L * 1000L);
                }
                if (allDay) {
                    startMillis = allDayMillisToLocalDayStart(startMillis);
                    endMillis = allDayMillisToLocalDayStart(endMillis);
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
                        TextUtils.isEmpty(title) ? AppText.untitled() : title,
                        description == null ? "" : description,
                        startMillis,
                        endMillis,
                        allDay
                ));
            }
        } finally {
            cursor.close();
        }

        return events;
    }

    public static List<CalendarEvent> getEventsForCalendar(Context context, long calendarId) {
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
                + CalendarContract.Events.DELETED + " != 1";
        String[] selectionArgs = {
                String.valueOf(calendarId)
        };
        String sortOrder = CalendarContract.Events.DTSTART + " ASC";

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
            int eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID);
            int calendarIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID);
            int titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE);
            int descriptionIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION);
            int startIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART);
            int endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND);
            int allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY);

            while (cursor.moveToNext()) {
                if (cursor.isNull(startIndex)) {
                    continue;
                }
                long startMillis = cursor.getLong(startIndex);
                long endMillis = cursor.isNull(endIndex) ? startMillis : cursor.getLong(endIndex);
                boolean allDay = cursor.getInt(allDayIndex) == 1;
                if (endMillis == startMillis) {
                    endMillis = startMillis + (allDay ? DAY_IN_MILLIS : 60L * 60L * 1000L);
                }
                if (allDay) {
                    startMillis = allDayMillisToLocalDayStart(startMillis);
                    endMillis = allDayMillisToLocalDayStart(endMillis);
                }

                String title = cursor.getString(titleIndex);
                String description = cursor.getString(descriptionIndex);
                long eventId = cursor.getLong(eventIdIndex);

                events.add(new CalendarEvent(
                        eventId,
                        eventId,
                        cursor.getLong(calendarIdIndex),
                        TextUtils.isEmpty(title) ? AppText.untitled() : title,
                        description == null ? "" : description,
                        startMillis,
                        endMillis,
                        allDay
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
        values.put(CalendarContract.Events.DTSTART, allDay ? localDateToUtcAllDayMillis(startMillis) : startMillis);
        values.put(CalendarContract.Events.DTEND, allDay ? localDateToUtcAllDayMillis(endMillis) : endMillis);
        values.put(CalendarContract.Events.ALL_DAY, allDay ? 1 : 0);
        values.put(CalendarContract.Events.EVENT_TIMEZONE, allDay ? UTC_TIME_ZONE.getID() : TimeZone.getDefault().getID());
        values.put(CalendarContract.Events.HAS_ALARM, 0);
        return values;
    }

    private static long localDateToUtcAllDayMillis(long localDateMillis) {
        Calendar localCalendar = Calendar.getInstance();
        localCalendar.setTimeInMillis(localDateMillis);

        Calendar utcCalendar = Calendar.getInstance(UTC_TIME_ZONE);
        utcCalendar.clear();
        utcCalendar.set(
                localCalendar.get(Calendar.YEAR),
                localCalendar.get(Calendar.MONTH),
                localCalendar.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
        );
        return utcCalendar.getTimeInMillis();
    }

    private static long allDayMillisToLocalDayStart(long millis) {
        if (isLocalMidnight(millis) && !isUtcMidnight(millis)) {
            return startOfLocalDay(millis);
        }

        Calendar utcCalendar = Calendar.getInstance(UTC_TIME_ZONE);
        utcCalendar.setTimeInMillis(millis);

        Calendar localCalendar = Calendar.getInstance();
        localCalendar.clear();
        localCalendar.set(
                utcCalendar.get(Calendar.YEAR),
                utcCalendar.get(Calendar.MONTH),
                utcCalendar.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
        );
        return localCalendar.getTimeInMillis();
    }

    private static boolean isLocalMidnight(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.HOUR_OF_DAY) == 0
                && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.SECOND) == 0
                && calendar.get(Calendar.MILLISECOND) == 0;
    }

    private static boolean isUtcMidnight(long millis) {
        Calendar calendar = Calendar.getInstance(UTC_TIME_ZONE);
        calendar.setTimeInMillis(millis);
        return calendar.get(Calendar.HOUR_OF_DAY) == 0
                && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.SECOND) == 0
                && calendar.get(Calendar.MILLISECOND) == 0;
    }

    private static long startOfLocalDay(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static final class CalendarInfo {
        public final long id;
        public final String displayName;
        public final String accountName;
        public final String ownerAccount;
        public final String accountType;
        public final String syncId;
        public final int accessLevel;

        CalendarInfo(long id, String displayName, String accountName, String ownerAccount, String accountType, String syncId, int accessLevel) {
            this.id = id;
            this.displayName = displayName == null ? AppText.unnamedCalendar() : displayName;
            this.accountName = accountName == null ? AppText.noAccount() : accountName;
            this.ownerAccount = ownerAccount == null ? "" : ownerAccount;
            this.accountType = accountType == null ? "" : accountType;
            this.syncId = syncId == null ? "" : syncId;
            this.accessLevel = accessLevel;
        }

        public boolean isGoogleCalendar() {
            return "com.google".equals(accountType);
        }

        public boolean canWrite() {
            return accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR;
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
