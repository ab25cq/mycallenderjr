package com.example.myhelloworld;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int REQUEST_CALENDAR_PERMISSIONS = 1001;
    private static final int REQUEST_EXPORT_TODOS = 2001;
    private static final int REQUEST_IMPORT_TODOS = 2002;
    private static final String PREFS_NAME = "calendar_app";
    private static final String KEY_SELECTED_CALENDAR_ID = "selected_calendar_id";
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;

    private final List<CalendarRepository.CalendarInfo> writableCalendars = new ArrayList<>();
    private final List<CalendarRepository.CalendarEvent> eventsForSelectedDay = new ArrayList<>();
    private final List<LocalTodoRepository.LocalTodo> todosForSelectedDay = new ArrayList<>();
    private final List<ScheduleListAdapter.ScheduleListItem> selectedDayItems = new ArrayList<>();
    private final List<MonthDayCell> monthDayCells = new ArrayList<>();

    private TextView permissionView;
    private TextView monthLabelView;
    private TextView selectedDateView;
    private GridView monthGridView;
    private ScheduleListAdapter scheduleListAdapter;
    private MonthCalendarAdapter monthCalendarAdapter;

    private long selectedDayMillis;
    private long selectedCalendarId = -1L;
    private long selectedEventId = -1L;
    private Calendar visibleMonth;
    private float monthTouchDownX;
    private float monthTouchDownY;
    private float scheduleTouchDownX;
    private float scheduleTouchDownY;
    private int scheduleTouchDownPosition = AdapterView.INVALID_POSITION;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        selectedDayMillis = startOfDay(System.currentTimeMillis());
        visibleMonth = Calendar.getInstance();
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);

        buildLayout();

        if (savedInstanceState != null) {
            selectedDayMillis = savedInstanceState.getLong("selected_day_millis", selectedDayMillis);
            selectedCalendarId = savedInstanceState.getLong("selected_calendar_id", loadPersistedCalendarId());
            selectedEventId = savedInstanceState.getLong("selected_event_id", -1L);
            visibleMonth.setTimeInMillis(savedInstanceState.getLong("visible_month_millis", visibleMonth.getTimeInMillis()));
            resetToMonthStart(visibleMonth);
        } else {
            selectedCalendarId = loadPersistedCalendarId();
        }

        if (hasCalendarPermissions()) {
            loadCalendars();
            refreshMonthGrid();
            refreshEventList();
        } else {
            showPermissionState();
            refreshMonthGrid();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCalendarPermissions()) {
            loadCalendars();
            refreshMonthGrid();
            refreshEventList();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("selected_day_millis", selectedDayMillis);
        outState.putLong("selected_calendar_id", selectedCalendarId);
        outState.putLong("selected_event_id", selectedEventId);
        outState.putLong("visible_month_millis", visibleMonth.getTimeInMillis());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CALENDAR_PERMISSIONS) {
            return;
        }

        boolean granted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                break;
            }
        }

        if (granted) {
            loadCalendars();
            refreshMonthGrid();
            refreshEventList();
        } else {
            showPermissionState();
            refreshMonthGrid();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_TODOS) {
            exportTodosToUri(uri);
            return;
        }

        if (requestCode == REQUEST_IMPORT_TODOS) {
            importTodosFromUri(uri);
        }
    }

    private void buildLayout() {
        int padding = dp(12);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        root.setPadding(padding, padding, padding, padding);

        permissionView = new TextView(this);
        permissionView.setTextSize(13);
        permissionView.setPadding(0, 0, 0, dp(8));

        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);

        Button previousMonthButton = buildCompactActionButton("＜");
        previousMonthButton.setOnClickListener(v -> shiftMonth(-1));

        monthLabelView = new TextView(this);
        monthLabelView.setGravity(Gravity.CENTER);
        monthLabelView.setTextSize(20);
        monthLabelView.setTypeface(monthLabelView.getTypeface(), android.graphics.Typeface.BOLD);

        Button nextMonthButton = buildCompactActionButton("＞");
        nextMonthButton.setOnClickListener(v -> shiftMonth(1));

        LinearLayout.LayoutParams sideButtonParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sideButtonParams.weight = 1f;

        LinearLayout.LayoutParams monthLabelParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        monthLabelParams.weight = 3f;

        monthBar.addView(previousMonthButton, sideButtonParams);
        monthBar.addView(monthLabelView, monthLabelParams);
        monthBar.addView(nextMonthButton, sideButtonParams);

        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setPadding(0, dp(8), 0, dp(8));

        Button calendarButton = buildCompactActionButton("同期先変更");
        calendarButton.setOnClickListener(v -> showCalendarPicker());

        Button refreshButton = buildCompactActionButton("再読み込み");
        refreshButton.setOnClickListener(v -> refreshAllData());

        controlBar.addView(calendarButton, createWeightedButtonLayoutParams());
        controlBar.addView(refreshButton, createWeightedButtonLayoutParams());

        GridLayout weekdayHeader = new GridLayout(this);
        weekdayHeader.setColumnCount(7);
        weekdayHeader.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        String[] weekLabels = {"月", "火", "水", "木", "金", "土", "日"};
        for (String weekLabel : weekLabels) {
            TextView labelView = new TextView(this);
            labelView.setText(weekLabel);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextSize(12);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setGravity(Gravity.FILL_HORIZONTAL);
            labelView.setLayoutParams(params);
            weekdayHeader.addView(labelView);
        }

        monthGridView = new GridView(this);
        monthGridView.setNumColumns(7);
        monthGridView.setHorizontalSpacing(dp(4));
        monthGridView.setVerticalSpacing(dp(4));
        monthGridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        monthGridView.setGravity(Gravity.CENTER);
        monthGridView.setVerticalScrollBarEnabled(false);
        monthGridView.setSelector(android.R.color.transparent);
        monthCalendarAdapter = new MonthCalendarAdapter(this, monthDayCells);
        monthGridView.setAdapter(monthCalendarAdapter);
        LinearLayout.LayoutParams monthGridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(380)
        );
        monthGridParams.topMargin = dp(4);
        monthGridView.setLayoutParams(monthGridParams);
        monthGridView.setOnItemClickListener((parent, view, position, id) -> {
            MonthDayCell cell = monthDayCells.get(position);
            selectedDayMillis = cell.dayStartMillis;
            selectedEventId = -1L;
            visibleMonth.setTimeInMillis(cell.dayStartMillis);
            resetToMonthStart(visibleMonth);
            refreshMonthGrid();
            refreshEventList();
        });
        monthGridView.setOnItemLongClickListener((parent, view, position, id) -> {
            MonthDayCell cell = monthDayCells.get(position);
            selectedDayMillis = cell.dayStartMillis;
            selectedEventId = -1L;
            visibleMonth.setTimeInMillis(cell.dayStartMillis);
            resetToMonthStart(visibleMonth);
            refreshMonthGrid();
            refreshEventList();
            showEventDialog(null);
            return true;
        });
        monthGridView.setOnTouchListener((v, event) -> handleMonthGridTouch(event));

        selectedDateView = new TextView(this);
        selectedDateView.setTextSize(15);
        selectedDateView.setTypeface(selectedDateView.getTypeface(), android.graphics.Typeface.BOLD);
        selectedDateView.setPadding(0, dp(8), 0, dp(6));

        FrameLayout listContainer = new FrameLayout(this);
        LinearLayout.LayoutParams listContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
        );
        listContainerParams.weight = 1f;
        listContainer.setLayoutParams(listContainerParams);

        ListView scheduleListView = new ListView(this);
        scheduleListView.setDividerHeight(dp(4));
        scheduleListView.setSelector(android.R.color.transparent);
        scheduleListAdapter = new ScheduleListAdapter(this, selectedDayItems);
        scheduleListView.setAdapter(scheduleListAdapter);
        scheduleListView.setOnItemClickListener((parent, view, position, id) -> {
            ScheduleListAdapter.ScheduleListItem item =
                    (ScheduleListAdapter.ScheduleListItem) scheduleListAdapter.getItem(position);
            if (!item.isEvent()) {
                return;
            }
            selectedEventId = item.event.id;
            scheduleListAdapter.setSelectedEventId(selectedEventId);
            scheduleListAdapter.notifyDataSetChanged();
        });
        scheduleListView.setOnItemLongClickListener((parent, view, position, id) -> {
            ScheduleListAdapter.ScheduleListItem item =
                    (ScheduleListAdapter.ScheduleListItem) scheduleListAdapter.getItem(position);
            if (item.isEvent()) {
                selectedEventId = item.event.id;
                scheduleListAdapter.setSelectedEventId(selectedEventId);
                scheduleListAdapter.notifyDataSetChanged();
                showEventActionsDialog(item.event);
                return true;
            }
            if (item.isTodo()) {
                showTodoActionsDialog(item.todo);
                return true;
            }
            return false;
        });

        listContainer.addView(scheduleListView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, dp(8), 0, 0);

        Button addButton = buildCompactActionButton("予定を追加");
        addButton.setOnClickListener(v -> showEventDialog(null));

        Button editButton = buildCompactActionButton("編集");
        editButton.setOnClickListener(v -> editSelectedEvent());

        Button deleteButton = buildCompactActionButton("削除");
        deleteButton.setOnClickListener(v -> deleteSelectedEvent());

        Button addTodoButton = buildCompactActionButton("TODO追加");
        addTodoButton.setOnClickListener(v -> showTodoDialog(null));

        Button exportTodoButton = buildCompactActionButton("TODO保存");
        exportTodoButton.setOnClickListener(v -> launchTodoExport());

        Button importTodoButton = buildCompactActionButton("TODO読込");
        importTodoButton.setOnClickListener(v -> confirmTodoImport());

        Button todayButton = buildCompactActionButton("本日");
        todayButton.setOnClickListener(v -> jumpToToday());

        actionBar.addView(addButton, createWeightedButtonLayoutParams());
        actionBar.addView(editButton, createWeightedButtonLayoutParams());
        actionBar.addView(deleteButton, createWeightedButtonLayoutParams());
        actionBar.addView(addTodoButton, createWeightedButtonLayoutParams());

        LinearLayout todoFileBar = new LinearLayout(this);
        todoFileBar.setOrientation(LinearLayout.HORIZONTAL);
        todoFileBar.setPadding(0, dp(6), 0, 0);

        todoFileBar.addView(exportTodoButton, createWeightedButtonLayoutParams());
        todoFileBar.addView(importTodoButton, createWeightedButtonLayoutParams());
        todoFileBar.addView(todayButton, createWeightedButtonLayoutParamsWithoutMargin());

        root.addView(permissionView);
        root.addView(monthBar);
        root.addView(controlBar);
        root.addView(weekdayHeader);
        root.addView(monthGridView);
        root.addView(selectedDateView);
        root.addView(listContainer);
        root.addView(actionBar);
        root.addView(todoFileBar);

        setContentView(root);
    }

    private void refreshAllData() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        loadCalendars();
        refreshMonthGrid();
        refreshEventList();
    }

    private void showPermissionState() {
        permissionView.setVisibility(View.VISIBLE);
        permissionView.setText("カレンダー権限を許可すると予定を表示して同期できます。");
        requestCalendarPermissions();
    }

    private void showCalendarUnavailableState() {
        permissionView.setVisibility(View.VISIBLE);
        permissionView.setText("書き込み可能なカレンダーがありません。端末で Google カレンダー同期を有効にしてください。");
    }

    private void requestCalendarPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        requestPermissions(
                new String[]{
                        Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR
                },
                REQUEST_CALENDAR_PERMISSIONS
        );
    }

    private boolean hasCalendarPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadCalendars() {
        if (!hasCalendarPermissions()) {
            showPermissionState();
            return;
        }

        writableCalendars.clear();
        writableCalendars.addAll(CalendarRepository.getWritableCalendars(this));

        if (writableCalendars.isEmpty()) {
            selectedCalendarId = -1L;
            persistCalendarId(selectedCalendarId);
            showCalendarUnavailableState();
            return;
        }

        permissionView.setVisibility(View.GONE);

        if (!containsCalendarId(selectedCalendarId)) {
            selectedCalendarId = pickInitialCalendarId();
            persistCalendarId(selectedCalendarId);
        }
    }

    private void refreshMonthGrid() {
        monthLabelView.setText(formatVisibleMonth(visibleMonth.getTimeInMillis()));

        monthDayCells.clear();
        long gridStartMillis = getGridStartMillis();
        Map<Long, List<CalendarRepository.CalendarEvent>> eventsByDay = new HashMap<>();

        if (hasCalendarPermissions() && selectedCalendarId >= 0L) {
            long gridEndMillis = gridStartMillis + (35L * DAY_IN_MILLIS);
            List<CalendarRepository.CalendarEvent> monthEvents =
                    CalendarRepository.getEventsForRange(this, selectedCalendarId, gridStartMillis, gridEndMillis);
            distributeEventsByDay(monthEvents, gridStartMillis, gridEndMillis, eventsByDay);
        }

        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(gridStartMillis);
        long todayStart = startOfDay(System.currentTimeMillis());

        for (int i = 0; i < 35; i++) {
            long dayStartMillis = dayCursor.getTimeInMillis();
            boolean isCurrentMonth = dayCursor.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH)
                    && dayCursor.get(Calendar.YEAR) == visibleMonth.get(Calendar.YEAR);
            List<CalendarRepository.CalendarEvent> dayEvents = eventsByDay.get(dayStartMillis);
            monthDayCells.add(new MonthDayCell(
                    dayStartMillis,
                    String.valueOf(dayCursor.get(Calendar.DAY_OF_MONTH)),
                    buildDaySummary(dayStartMillis, dayEvents),
                    isCurrentMonth,
                    dayStartMillis == todayStart,
                    dayStartMillis == selectedDayMillis
            ));
            dayCursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        monthCalendarAdapter.notifyDataSetChanged();
    }

    private void distributeEventsByDay(
            List<CalendarRepository.CalendarEvent> monthEvents,
            long gridStartMillis,
            long gridEndMillis,
            Map<Long, List<CalendarRepository.CalendarEvent>> eventsByDay
    ) {
        for (CalendarRepository.CalendarEvent event : monthEvents) {
            long cursor = Math.max(startOfDay(event.startMillis), gridStartMillis);
            long eventEndExclusive = Math.min(gridEndMillis, Math.max(event.endMillis, event.startMillis + 1L));

            while (cursor < eventEndExclusive) {
                long dayEnd = cursor + DAY_IN_MILLIS;
                if (event.startMillis < dayEnd && eventEndExclusive > cursor) {
                    List<CalendarRepository.CalendarEvent> dayEvents = eventsByDay.get(cursor);
                    if (dayEvents == null) {
                        dayEvents = new ArrayList<>();
                        eventsByDay.put(cursor, dayEvents);
                    }
                    dayEvents.add(event);
                }
                cursor += DAY_IN_MILLIS;
            }
        }
    }

    private String buildDaySummary(long dayStartMillis, List<CalendarRepository.CalendarEvent> dayEvents) {
        if (dayEvents == null || dayEvents.isEmpty()) {
            return "";
        }

        List<CalendarRepository.CalendarEvent> orderedEvents = EventOrderRepository.sortEvents(
                this,
                selectedCalendarId,
                dayStartMillis,
                dayEvents
        );

        StringBuilder builder = new StringBuilder();
        int visibleCount = Math.min(3, orderedEvents.size());
        for (int i = 0; i < visibleCount; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(orderedEvents.get(i).title);
        }
        return builder.toString();
    }

    private void refreshEventList() {
        selectedDateView.setText(formatSelectedDate(selectedDayMillis));

        if (!hasCalendarPermissions() || selectedCalendarId < 0L) {
            eventsForSelectedDay.clear();
            todosForSelectedDay.clear();
            selectedEventId = -1L;
            refreshSelectedDayItems();
            return;
        }

        eventsForSelectedDay.clear();
        eventsForSelectedDay.addAll(CalendarRepository.getEventsForDay(this, selectedCalendarId, selectedDayMillis));
        List<CalendarRepository.CalendarEvent> orderedEvents = EventOrderRepository.sortEvents(
                this,
                selectedCalendarId,
                selectedDayMillis,
                eventsForSelectedDay
        );
        eventsForSelectedDay.clear();
        eventsForSelectedDay.addAll(orderedEvents);
        todosForSelectedDay.clear();
        todosForSelectedDay.addAll(LocalTodoRepository.getTodos(this));

        if (!containsEventId(selectedEventId)) {
            selectedEventId = eventsForSelectedDay.isEmpty() ? -1L : eventsForSelectedDay.get(0).id;
        }

        refreshSelectedDayItems();
    }

    private void refreshSelectedDayItems() {
        selectedDayItems.clear();

        selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.section("予定"));
        if (eventsForSelectedDay.isEmpty()) {
            selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.message("カレンダー予定はありません。"));
        } else {
            for (CalendarRepository.CalendarEvent event : eventsForSelectedDay) {
                selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.event(this, event));
            }
        }

        selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.section("TODO"));
        if (todosForSelectedDay.isEmpty()) {
            selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.message("TODO はありません。"));
        } else {
            for (LocalTodoRepository.LocalTodo todo : todosForSelectedDay) {
                selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.todo(todo));
            }
        }

        scheduleListAdapter.setSelectedEventId(selectedEventId);
        scheduleListAdapter.notifyDataSetChanged();
    }

    private void showCalendarPicker() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        if (writableCalendars.isEmpty()) {
            Toast.makeText(this, "選択できるカレンダーがありません", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[writableCalendars.size()];
        int checkedItem = 0;
        for (int i = 0; i < writableCalendars.size(); i++) {
            CalendarRepository.CalendarInfo info = writableCalendars.get(i);
            labels[i] = info.displayName + " / " + info.accountName;
            if (info.id == selectedCalendarId) {
                checkedItem = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("同期先カレンダーを選択")
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    selectedCalendarId = writableCalendars.get(which).id;
                    persistCalendarId(selectedCalendarId);
                    selectedEventId = -1L;
                    loadCalendars();
                    refreshMonthGrid();
                    refreshEventList();
                    dialog.dismiss();
                })
                .setNegativeButton("閉じる", null)
                .show();
    }

    private void showEventDialog(CalendarRepository.CalendarEvent existingEvent) {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        if (selectedCalendarId < 0L) {
            Toast.makeText(this, "先に同期先カレンダーを選んでください", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean editing = existingEvent != null;
        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        if (editing) {
            startCalendar.setTimeInMillis(existingEvent.startMillis);
            endCalendar.setTimeInMillis(existingEvent.endMillis);
        } else {
            startCalendar.setTimeInMillis(defaultStartTime(selectedDayMillis));
            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
        }

        int padding = dp(16);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, 0);

        EditText titleInput = new EditText(this);
        titleInput.setHint("予定タイトル");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (editing) {
            titleInput.setText(existingEvent.title);
        }

        EditText notesInput = new EditText(this);
        notesInput.setHint("メモ");
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (editing) {
            notesInput.setText(existingEvent.description);
        }

        CheckBox allDayCheck = new CheckBox(this);
        allDayCheck.setText("終日予定");
        allDayCheck.setChecked(editing && existingEvent.allDay);

        Button startDateButton = buildActionButton("");
        Button startTimeButton = buildActionButton("");
        Button endDateButton = buildActionButton("");

        Runnable bindDateTimeLabels = () -> {
            startDateButton.setText("開始日: " + formatDate(startCalendar.getTimeInMillis()));
            startTimeButton.setText("開始時刻: " + formatTime(startCalendar.getTimeInMillis()));
            endDateButton.setText("終了日: " + formatDate(endCalendar.getTimeInMillis()));
            startTimeButton.setVisibility(allDayCheck.isChecked() ? View.GONE : View.VISIBLE);
        };

        startDateButton.setOnClickListener(v -> showDatePicker(startCalendar, bindDateTimeLabels));
        endDateButton.setOnClickListener(v -> showDatePicker(endCalendar, bindDateTimeLabels));
        startTimeButton.setOnClickListener(v -> showTimePicker(startCalendar, () -> {
            syncTimeOfDay(startCalendar, endCalendar);
            bindDateTimeLabels.run();
        }));
        allDayCheck.setOnCheckedChangeListener((buttonView, isChecked) -> bindDateTimeLabels.run());
        if (!allDayCheck.isChecked()) {
            syncTimeOfDay(startCalendar, endCalendar);
        }
        bindDateTimeLabels.run();

        addFormField(form, titleInput);
        addFormField(form, notesInput);
        addFormField(form, allDayCheck);
        addFormField(form, startDateButton);
        addFormField(form, startTimeButton);
        addFormField(form, endDateButton);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "予定を編集" : "予定を追加")
                .setView(form)
                .setPositiveButton(editing ? "更新" : "保存", null)
                .setNegativeButton("キャンセル", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            titleInput.requestFocus();
            titleInput.setSelection(titleInput.getText().length());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            titleInput.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = titleInput.getText().toString().trim();
                String description = notesInput.getText().toString().trim();
                boolean allDay = allDayCheck.isChecked();

                if (TextUtils.isEmpty(title)) {
                    titleInput.setError("タイトルを入力してください");
                    return;
                }

                long startMillis = allDay ? startOfDay(startCalendar.getTimeInMillis()) : startCalendar.getTimeInMillis();
                long endMillis = allDay
                        ? startOfDay(endCalendar.getTimeInMillis()) + DAY_IN_MILLIS
                        : buildDateTimeWithTimeOfDay(endCalendar, startCalendar);

                if ((allDay && endMillis <= startMillis) || (!allDay && endMillis < startMillis)) {
                    Toast.makeText(this, "終了日は開始日以降にしてください", Toast.LENGTH_LONG).show();
                    return;
                }

                boolean success;
                if (editing) {
                    success = CalendarRepository.updateEvent(
                            this,
                            existingEvent.id,
                            selectedCalendarId,
                            title,
                            description,
                            startMillis,
                            endMillis,
                            allDay
                    );
                } else {
                    long newEventId = CalendarRepository.insertEvent(
                            this,
                            selectedCalendarId,
                            title,
                            description,
                            startMillis,
                            endMillis,
                            allDay
                    );
                    success = newEventId >= 0L;
                    if (success) {
                        selectedEventId = newEventId;
                    }
                }

                if (!success) {
                    Toast.makeText(this, "予定の保存に失敗しました", Toast.LENGTH_LONG).show();
                    return;
                }

                selectedDayMillis = startOfDay(startMillis);
                visibleMonth.setTimeInMillis(selectedDayMillis);
                resetToMonthStart(visibleMonth);
                loadCalendars();
                refreshMonthGrid();
                refreshEventList();
                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private void showTodoDialog(LocalTodoRepository.LocalTodo existingTodo) {
        boolean editing = existingTodo != null;
        int padding = dp(16);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, 0);

        EditText titleInput = new EditText(this);
        titleInput.setHint("TODOタイトル");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (editing) {
            titleInput.setText(existingTodo.title);
        }

        addFormField(form, titleInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "TODO を編集" : "TODO を追加")
                .setView(form)
                .setPositiveButton(editing ? "更新" : "追加", null)
                .setNegativeButton("キャンセル", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            titleInput.requestFocus();
            titleInput.setSelection(titleInput.getText().length());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            titleInput.post(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(titleInput, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = titleInput.getText().toString().trim();
                if (TextUtils.isEmpty(title)) {
                    titleInput.setError("タイトルを入力してください");
                    return;
                }

                boolean success;
                if (editing) {
                    success = LocalTodoRepository.updateTodo(this, existingTodo.id, title);
                } else {
                    LocalTodoRepository.addTodo(this, title);
                    success = true;
                }

                if (!success) {
                    Toast.makeText(this, "TODO の保存に失敗しました", Toast.LENGTH_LONG).show();
                    return;
                }

                refreshEventList();
                dialog.dismiss();
                Toast.makeText(this, editing ? "TODO を更新しました" : "TODO を保存しました", Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private void showTodoActionsDialog(LocalTodoRepository.LocalTodo todo) {
        String[] actions = {"編集", "上へ移動", "下へ移動", "削除"};
        new AlertDialog.Builder(this)
                .setTitle(todo.title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showTodoDialog(todo);
                        return;
                    }

                    if (which == 3) {
                        confirmTodoDelete(todo);
                        return;
                    }

                    int offset = which == 1 ? -1 : 1;
                    boolean moved = LocalTodoRepository.moveTodo(this, todo.id, offset);
                    if (moved) {
                        refreshEventList();
                        Toast.makeText(this, "TODO の順番を変更しました", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "これ以上は移動できません", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void confirmTodoDelete(LocalTodoRepository.LocalTodo todo) {
        new AlertDialog.Builder(this)
                .setTitle("TODO を削除")
                .setMessage("「" + todo.title + "」を削除しますか。")
                .setPositiveButton("削除", (dialog, which) -> {
                    boolean deleted = LocalTodoRepository.deleteTodo(this, todo.id);
                    if (deleted) {
                        refreshEventList();
                        Toast.makeText(this, "TODO を削除しました", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "TODO の削除に失敗しました", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void editSelectedEvent() {
        CalendarRepository.CalendarEvent event = getSelectedEvent();
        if (event == null) {
            Toast.makeText(this, "編集する予定を選んでください", Toast.LENGTH_SHORT).show();
            return;
        }
        showEventDialog(event);
    }

    private void showEventActionsDialog(CalendarRepository.CalendarEvent event) {
        String[] actions = {"編集", "上へ移動", "下へ移動", "削除"};
        new AlertDialog.Builder(this)
                .setTitle(event.title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showEventDialog(event);
                        return;
                    }

                    if (which == 3) {
                        showDeleteEventDialog(event);
                        return;
                    }

                    int offset = which == 1 ? -1 : 1;
                    boolean moved = EventOrderRepository.moveEvent(
                            this,
                            selectedCalendarId,
                            selectedDayMillis,
                            eventsForSelectedDay,
                            event.id,
                            offset
                    );
                    if (moved) {
                        refreshMonthGrid();
                        refreshEventList();
                        Toast.makeText(this, "予定の順番を変更しました", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "これ以上は移動できません", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void deleteSelectedEvent() {
        CalendarRepository.CalendarEvent event = getSelectedEvent();
        if (event == null) {
            Toast.makeText(this, "削除する予定を選んでください", Toast.LENGTH_SHORT).show();
            return;
        }

        showDeleteEventDialog(event);
    }

    private void showDeleteEventDialog(CalendarRepository.CalendarEvent event) {
        new AlertDialog.Builder(this)
                .setTitle("予定を削除")
                .setMessage("「" + event.title + "」を削除しますか。")
                .setPositiveButton("削除", (dialog, which) -> {
                    boolean deleted = CalendarRepository.deleteEvent(this, event.id);
                    if (deleted) {
                        selectedEventId = -1L;
                        refreshMonthGrid();
                        refreshEventList();
                    } else {
                        Toast.makeText(this, "予定の削除に失敗しました", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private boolean handleMonthGridTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                monthTouchDownX = event.getX();
                monthTouchDownY = event.getY();
                return false;
            case MotionEvent.ACTION_UP:
                float deltaX = event.getX() - monthTouchDownX;
                float deltaY = event.getY() - monthTouchDownY;
                if (Math.abs(deltaX) > dp(64) && Math.abs(deltaY) < dp(48)) {
                    shiftMonth(deltaX < 0 ? 1 : -1);
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                return false;
            default:
                return false;
        }
    }

    private void shiftMonth(int delta) {
        visibleMonth.add(Calendar.MONTH, delta);
        resetToMonthStart(visibleMonth);
        refreshMonthGrid();
    }

    private void jumpToToday() {
        selectedDayMillis = startOfDay(System.currentTimeMillis());
        selectedEventId = -1L;
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);
        refreshMonthGrid();
        refreshEventList();
    }

    private void showDatePicker(Calendar calendar, Runnable onChanged) {
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    onChanged.run();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void showTimePicker(Calendar calendar, Runnable onChanged) {
        boolean is24Hour = DateFormat.is24HourFormat(this);
        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    calendar.set(Calendar.MINUTE, minute);
                    calendar.set(Calendar.SECOND, 0);
                    calendar.set(Calendar.MILLISECOND, 0);
                    onChanged.run();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                is24Hour
        ).show();
    }

    private void addFormField(LinearLayout form, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(10);
        form.addView(view, params);
    }

    private Button buildActionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private Button buildCompactActionButton(String text) {
        Button button = buildActionButton(text);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), dp(6), dp(8), dp(6));
        return button;
    }

    private LinearLayout.LayoutParams createWeightedButtonLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = 1f;
        params.rightMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams createWeightedButtonLayoutParamsWithoutMargin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = 1f;
        return params;
    }

    private void launchTodoExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, buildTodoBackupFileName());
        startActivityForResult(intent, REQUEST_EXPORT_TODOS);
    }

    private void confirmTodoImport() {
        new AlertDialog.Builder(this)
                .setTitle("TODO を読み込み")
                .setMessage("ファイルの内容で現在の TODO を置き換えます。")
                .setPositiveButton("読込", (dialog, which) -> launchTodoImport())
                .setNegativeButton("キャンセル", null)
                .show();
    }

    private void launchTodoImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_TODOS);
    }

    private void exportTodosToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("output stream is null");
            }

            String json = LocalTodoRepository.exportTodosAsJson(this);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Toast.makeText(this, "TODO を保存しました", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "TODO の保存に失敗しました", Toast.LENGTH_LONG).show();
        }
    }

    private void importTodosFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("input stream is null");
            }

            String json = readAllText(inputStream);
            int importedCount = LocalTodoRepository.importTodosFromJson(this, json);
            refreshEventList();
            Toast.makeText(this, importedCount + " 件の TODO を読み込みました", Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "TODO の読み込みに失敗しました", Toast.LENGTH_LONG).show();
        }
    }

    private String readAllText(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int readSize;
        while ((readSize = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, readSize);
        }
        return outputStream.toString(StandardCharsets.UTF_8.name());
    }

    private String buildTodoBackupFileName() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault());
        return "todo_backup_" + format.format(new Date()) + ".json";
    }

    private CalendarRepository.CalendarEvent getSelectedEvent() {
        for (CalendarRepository.CalendarEvent event : eventsForSelectedDay) {
            if (event.id == selectedEventId) {
                return event;
            }
        }
        return null;
    }

    private boolean containsCalendarId(long calendarId) {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.id == calendarId) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEventId(long eventId) {
        for (CalendarRepository.CalendarEvent event : eventsForSelectedDay) {
            if (event.id == eventId) {
                return true;
            }
        }
        return false;
    }

    private long pickInitialCalendarId() {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.isGoogleCalendar()) {
                return info.id;
            }
        }
        return writableCalendars.get(0).id;
    }

    private long loadPersistedCalendarId() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getLong(KEY_SELECTED_CALENDAR_ID, -1L);
    }

    private void persistCalendarId(long calendarId) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putLong(KEY_SELECTED_CALENDAR_ID, calendarId).apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private long startOfDay(long timeInMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long defaultStartTime(long dayMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 9);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void syncTimeOfDay(Calendar source, Calendar target) {
        target.set(Calendar.HOUR_OF_DAY, source.get(Calendar.HOUR_OF_DAY));
        target.set(Calendar.MINUTE, source.get(Calendar.MINUTE));
        target.set(Calendar.SECOND, source.get(Calendar.SECOND));
        target.set(Calendar.MILLISECOND, source.get(Calendar.MILLISECOND));
    }

    private long buildDateTimeWithTimeOfDay(Calendar dateSource, Calendar timeSource) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dateSource.getTimeInMillis());
        syncTimeOfDay(timeSource, calendar);
        return calendar.getTimeInMillis();
    }

    private long getGridStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(visibleMonth.getTimeInMillis());
        resetToMonthStart(calendar);
        int offset = getMondayBasedDayOffset(calendar.get(Calendar.DAY_OF_WEEK));
        calendar.add(Calendar.DAY_OF_MONTH, -offset);
        return startOfDay(calendar.getTimeInMillis());
    }

    private int getMondayBasedDayOffset(int dayOfWeek) {
        return (dayOfWeek + 5) % 7;
    }

    private void resetToMonthStart(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatSelectedDate(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy年M月d日 (E)", Locale.getDefault());
        return format.format(new Date(timeInMillis)) + " の予定";
    }

    private String formatVisibleMonth(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy年M月", Locale.getDefault());
        return format.format(new Date(timeInMillis));
    }

    private String formatDate(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        return format.format(new Date(timeInMillis));
    }

    private String formatTime(long timeInMillis) {
        java.text.DateFormat format = DateFormat.getTimeFormat(this);
        return format.format(new Date(timeInMillis));
    }
}
