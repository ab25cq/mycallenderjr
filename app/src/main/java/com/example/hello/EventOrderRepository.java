package com.example.myhelloworld;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EventOrderRepository {
    private static final String PREFS_NAME = "event_display_order";
    private static final String KEY_PREFIX = "order_";

    private EventOrderRepository() {
    }

    public static List<CalendarRepository.CalendarEvent> sortEvents(
            Context context,
            long calendarId,
            long dayStartMillis,
            List<CalendarRepository.CalendarEvent> events
    ) {
        List<Long> savedOrder = getSavedOrder(context, calendarId, dayStartMillis);
        Map<Long, CalendarRepository.CalendarEvent> eventsById = new LinkedHashMap<>();
        for (CalendarRepository.CalendarEvent event : events) {
            eventsById.put(event.id, event);
        }

        List<CalendarRepository.CalendarEvent> ordered = new ArrayList<>(events.size());
        for (Long eventId : savedOrder) {
            CalendarRepository.CalendarEvent event = eventsById.remove(eventId);
            if (event != null) {
                ordered.add(event);
            }
        }

        ordered.addAll(eventsById.values());
        return ordered;
    }

    public static boolean moveEvent(
            Context context,
            long calendarId,
            long dayStartMillis,
            List<CalendarRepository.CalendarEvent> currentEvents,
            long eventId,
            int offset
    ) {
        if (offset == 0) {
            return false;
        }

        List<CalendarRepository.CalendarEvent> ordered = sortEvents(
                context,
                calendarId,
                dayStartMillis,
                currentEvents
        );

        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).id == eventId) {
                int targetIndex = i + offset;
                if (targetIndex < 0 || targetIndex >= ordered.size()) {
                    return false;
                }

                CalendarRepository.CalendarEvent moving = ordered.remove(i);
                ordered.add(targetIndex, moving);
                saveOrder(context, calendarId, dayStartMillis, ordered);
                return true;
            }
        }

        return false;
    }

    private static List<Long> getSavedOrder(Context context, long calendarId, long dayStartMillis) {
        List<Long> order = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(buildKey(calendarId, dayStartMillis), "[]");

        try {
            JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                order.add(jsonArray.getLong(i));
            }
        } catch (JSONException ignored) {
        }

        return order;
    }

    private static void saveOrder(
            Context context,
            long calendarId,
            long dayStartMillis,
            List<CalendarRepository.CalendarEvent> orderedEvents
    ) {
        JSONArray jsonArray = new JSONArray();
        for (CalendarRepository.CalendarEvent event : orderedEvents) {
            jsonArray.put(event.id);
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit()
                .putString(buildKey(calendarId, dayStartMillis), jsonArray.toString())
                .apply();
    }

    private static String buildKey(long calendarId, long dayStartMillis) {
        return KEY_PREFIX + calendarId + "_" + dayStartMillis;
    }
}
