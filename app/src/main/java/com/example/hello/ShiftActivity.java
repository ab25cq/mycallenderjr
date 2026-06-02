package com.example.myhelloworld;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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

public class ShiftActivity extends Activity {
    private static final int REQUEST_CALENDAR_PERMISSIONS = 3001;
    private static final int REQUEST_EXPORT_SHIFT_BUTTONS = 3002;
    private static final int REQUEST_IMPORT_SHIFT_BUTTONS = 3003;
    private static final String PREFS_NAME = "calendar_app";
    private static final String KEY_SELECTED_CALENDAR_ID = "selected_calendar_id";
    private static final String CUSTOM_SYNC_TARGETS_KEY = "custom_sync_targets";
    private static final String SHIFT_PREFS_NAME = "shift_buttons";
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final int SHIFT_MONTH_CELL_HEIGHT_DP = 62;
    private static final int SHIFT_MONTH_SUMMARY_LINES = 3;
    private static final int SHIFT_MONTH_GRID_SPACING_DP = 1;
    private static final int MIN_MONTH_WEEK_ROWS = 5;
    private static final int MAX_MONTH_WEEK_ROWS = 6;
    private static final int SHIFT_MONTH_EVENTS_PER_DAY = 3;
    private static final int KEYBOARD_VISIBILITY_THRESHOLD_DP = 120;

    private final List<CalendarRepository.CalendarInfo> writableCalendars = new ArrayList<>();
    private final ShiftDefinition[] shiftDefinitions = new ShiftDefinition[]{
            new ShiftDefinition("早番", "07:00", "15:00", false),
            new ShiftDefinition("日勤", "09:00", "17:00", false),
            new ShiftDefinition("遅番", "13:00", "21:00", false),
            new ShiftDefinition("準夜", "16:00", "00:00", false),
            new ShiftDefinition("夜勤", "22:00", "07:00", false),
            new ShiftDefinition("明け", "", "", true),
            new ShiftDefinition("休み", "", "", true),
            new ShiftDefinition("有休", "", "", true),
            new ShiftDefinition("公休", "", "", true),
            new ShiftDefinition("希望休", "", "", true),
            new ShiftDefinition("研修", "09:00", "17:00", false),
            new ShiftDefinition("出張", "09:00", "17:00", false),
            new ShiftDefinition("会議", "09:00", "12:00", false),
            new ShiftDefinition("欠勤", "", "", true),
            new ShiftDefinition("その他", "", "", true)
    };
    private final List<Button> shiftButtons = new ArrayList<>();
    private final List<MonthDayCell> monthDayCells = new ArrayList<>();
    private final Map<Long, List<CalendarRepository.CalendarEvent>> monthEventsByDay = new HashMap<>();

    private long selectedCalendarId = -1L;
    private long selectedDayMillis;
    private Calendar visibleMonth;
    private TextView dateView;
    private TextView calendarView;
    private TextView statusView;
    private GridView monthGridView;
    private MonthCalendarAdapter monthCalendarAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        selectedDayMillis = startOfDay(System.currentTimeMillis());
        visibleMonth = Calendar.getInstance();
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);
        if (savedInstanceState != null) {
            selectedDayMillis = savedInstanceState.getLong("selected_day_millis", selectedDayMillis);
            selectedCalendarId = savedInstanceState.getLong("selected_calendar_id", loadPersistedCalendarId());
            visibleMonth.setTimeInMillis(savedInstanceState.getLong("visible_month_millis", visibleMonth.getTimeInMillis()));
            resetToMonthStart(visibleMonth);
        } else {
            selectedCalendarId = loadPersistedCalendarId();
        }
        loadShiftDefinitions();
        buildLayout();
        if (hasCalendarPermissions()) {
            loadCalendars();
        } else {
            requestCalendarPermissions();
        }
        refreshScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCalendarPermissions()) {
            loadCalendars();
            refreshScreen();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("selected_day_millis", selectedDayMillis);
        outState.putLong("selected_calendar_id", selectedCalendarId);
        outState.putLong("visible_month_millis", visibleMonth.getTimeInMillis());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_SHIFT_BUTTONS) {
            exportShiftButtonsToUri(uri);
            return;
        }

        if (requestCode == REQUEST_IMPORT_SHIFT_BUTTONS) {
            importShiftButtonsFromUri(uri);
        }
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
        } else {
            Toast.makeText(this, AppText.pick(this,
                    "カレンダー権限を許可してください",
                    "Allow calendar permission"), Toast.LENGTH_LONG).show();
        }
        refreshScreen();
    }

    private void buildLayout() {
        int padding = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText(AppText.pick(this, "シフト入力", "Shift entry"));
        titleView.setTextSize(20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(titleView, matchWrapParams());

        calendarView = new TextView(this);
        calendarView.setTextSize(12);
        calendarView.setGravity(Gravity.CENTER);
        calendarView.setPadding(0, dp(4), 0, dp(4));
        root.addView(calendarView, matchWrapParams());

        LinearLayout dateBar = new LinearLayout(this);
        dateBar.setOrientation(LinearLayout.HORIZONTAL);
        dateBar.setGravity(Gravity.CENTER_VERTICAL);

        Button previousButton = buildButton("＜");
        previousButton.setOnClickListener(v -> moveSelectedDay(-1));
        Button nextButton = buildButton("＞");
        nextButton.setOnClickListener(v -> moveSelectedDay(1));

        dateView = new TextView(this);
        dateView.setGravity(Gravity.CENTER);
        dateView.setTextSize(18);
        dateView.setTypeface(dateView.getTypeface(), android.graphics.Typeface.BOLD);
        dateView.setOnClickListener(v -> showShiftDatePicker());

        dateBar.addView(previousButton, weightedParams(1f, true));
        dateBar.addView(dateView, weightedParams(3f, true));
        dateBar.addView(nextButton, weightedParams(1f, false));
        root.addView(dateBar, matchWrapParams());

        GridLayout weekdayHeader = new GridLayout(this);
        weekdayHeader.setColumnCount(7);
        String[] weekLabels = AppText.weekLabels(this);
        for (String weekLabel : weekLabels) {
            TextView labelView = new TextView(this);
            labelView.setText(weekLabel);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextSize(9);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setGravity(Gravity.FILL_HORIZONTAL);
            weekdayHeader.addView(labelView, params);
        }
        root.addView(weekdayHeader, matchWrapParams());

        monthGridView = new GridView(this);
        monthGridView.setNumColumns(7);
        monthGridView.setHorizontalSpacing(dp(SHIFT_MONTH_GRID_SPACING_DP));
        monthGridView.setVerticalSpacing(dp(SHIFT_MONTH_GRID_SPACING_DP));
        monthGridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        monthGridView.setSelector(android.R.color.transparent);
        monthGridView.setVerticalScrollBarEnabled(false);
        monthCalendarAdapter = new MonthCalendarAdapter(this, monthDayCells);
        monthCalendarAdapter.setDisplayDensity(SHIFT_MONTH_CELL_HEIGHT_DP, SHIFT_MONTH_SUMMARY_LINES);
        monthCalendarAdapter.setWrapSummaryText(false);
        monthGridView.setAdapter(monthCalendarAdapter);
        monthGridView.setOnItemClickListener((parent, view, position, id) -> {
            MonthDayCell cell = monthDayCells.get(position);
            selectedDayMillis = cell.dayStartMillis;
            visibleMonth.setTimeInMillis(selectedDayMillis);
            resetToMonthStart(visibleMonth);
            refreshScreen();
        });
        root.addView(monthGridView, monthGridParams(MIN_MONTH_WEEK_ROWS));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        grid.setPadding(0, dp(8), 0, dp(4));
        shiftButtons.clear();
        for (int i = 0; i < shiftDefinitions.length; i++) {
            final int index = i;
            Button button = buildButton("");
            button.setTextSize(10);
            button.setMinHeight(dp(48));
            button.setPadding(dp(2), dp(2), dp(2), dp(2));
            button.setOnClickListener(v -> insertShift(index));
            button.setOnLongClickListener(v -> {
                showEditShiftDialog(index);
                return true;
            });
            shiftButtons.add(button);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = GridLayout.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 0, dp(3), dp(5));
            grid.addView(button, params);
        }
        root.addView(grid, matchWrapParams());

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);

        Button todayButton = buildButton(AppText.pick(this, "本日", "Today"));
        todayButton.setOnClickListener(v -> {
            selectedDayMillis = startOfDay(System.currentTimeMillis());
            refreshScreen();
        });
        Button customizeButton = buildButton(AppText.pick(this, "ボタン編集", "Edit buttons"));
        customizeButton.setOnClickListener(v -> showShiftButtonPicker());
        Button deleteShiftButton = buildButton(AppText.pick(this, "削除", "Delete"));
        deleteShiftButton.setOnClickListener(v -> deleteShiftButtonEventsForSelectedDay());
        Button exportButton = buildButton(AppText.pick(this, "保存", "Save"));
        exportButton.setOnClickListener(v -> launchShiftButtonExport());
        Button importButton = buildButton(AppText.pick(this, "読込", "Load"));
        importButton.setOnClickListener(v -> launchShiftButtonImport());

        actionBar.addView(todayButton, weightedParams(1f, true));
        actionBar.addView(customizeButton, weightedParams(1f, true));
        actionBar.addView(deleteShiftButton, weightedParams(1f, true));
        actionBar.addView(exportButton, weightedParams(1f, true));
        actionBar.addView(importButton, weightedParams(1f, false));
        root.addView(actionBar, matchWrapParams());

        statusView = new TextView(this);
        statusView.setTextSize(12);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(10), 0, dp(4));
        root.addView(statusView, matchWrapParams());

        Button backButton = buildButton(AppText.pick(this, "戻る", "Back"));
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, matchWrapParams());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        setContentView(scrollView);
        installSystemAwareRootPadding(scrollView);
    }

    private void refreshScreen() {
        if (dateView != null) {
            dateView.setText(formatDateTitle(selectedDayMillis));
        }
        if (calendarView != null) {
            calendarView.setText(getCalendarLabel());
        }
        if (statusView != null) {
            statusView.setText(AppText.pick(this,
                    "カレンダーで日付を選び、シフトボタンを押すと登録して翌日に進みます。",
                    "Select a date in the calendar, then tap a shift button to save and advance to the next day."));
        }
        for (int i = 0; i < shiftButtons.size(); i++) {
            shiftButtons.get(i).setText(shiftDefinitions[i].buttonLabel(this));
        }
        refreshMonthGrid();
    }

    private void insertShift(int index) {
        if (!ensureSelectedCalendarWritable()) {
            return;
        }
        ShiftDefinition shift = shiftDefinitions[index];
        String title = shift.title.trim();
        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, AppText.pick(this,
                    "シフト名を設定してください",
                    "Set a shift name"), Toast.LENGTH_SHORT).show();
            showEditShiftDialog(index);
            return;
        }

        long startMillis = selectedDayMillis;
        long endMillis = selectedDayMillis + DAY_IN_MILLIS;
        boolean allDay = shift.allDay;
        if (!allDay) {
            int[] startTime = parseTime(shift.startTime);
            int[] endTime = parseTime(shift.endTime);
            if (startTime == null || endTime == null) {
                Toast.makeText(this, AppText.pick(this,
                        "時刻は HH:mm で設定してください",
                        "Set time as HH:mm"), Toast.LENGTH_LONG).show();
                showEditShiftDialog(index);
                return;
            }
            startMillis = buildDateTime(selectedDayMillis, startTime[0], startTime[1]);
            endMillis = buildDateTime(selectedDayMillis, endTime[0], endTime[1]);
            if (endMillis <= startMillis) {
                endMillis += DAY_IN_MILLIS;
            }
        }

        long eventId = CalendarRepository.insertEvent(
                this,
                selectedCalendarId,
                title,
                AppText.pick(this, "シフト入力から登録", "Saved from shift entry"),
                startMillis,
                endMillis,
                allDay
        );
        if (eventId < 0L) {
            Toast.makeText(this, AppText.pick(this,
                    "シフトの登録に失敗しました",
                    "Failed to save shift"), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(this, AppText.pick(this,
                formatShortDate(selectedDayMillis) + " に " + title + " を登録しました",
                "Saved " + title + " on " + formatShortDate(selectedDayMillis)), Toast.LENGTH_SHORT).show();
        selectedDayMillis += DAY_IN_MILLIS;
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);
        refreshScreen();
    }

    private void showShiftButtonPicker() {
        String[] labels = new String[shiftDefinitions.length];
        for (int i = 0; i < shiftDefinitions.length; i++) {
            labels[i] = shiftDefinitions[i].buttonLabel(this);
        }
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "編集するシフトボタン", "Edit shift button"))
                .setItems(labels, (dialog, which) -> showEditShiftDialog(which))
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void showEditShiftDialog(int index) {
        ShiftDefinition shift = shiftDefinitions[index];
        int padding = dp(16);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, 0);

        EditText titleInput = new EditText(this);
        titleInput.setHint(AppText.pick(this, "シフト名", "Shift name"));
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT);
        titleInput.setText(shift.title);

        EditText startInput = new EditText(this);
        startInput.setHint(AppText.pick(this, "開始 HH:mm", "Start HH:mm"));
        startInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        startInput.setText(shift.startTime);

        EditText endInput = new EditText(this);
        endInput.setHint(AppText.pick(this, "終了 HH:mm", "End HH:mm"));
        endInput.setInputType(InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_TIME);
        endInput.setText(shift.endTime);

        CheckBox allDayCheck = new CheckBox(this);
        allDayCheck.setText(AppText.pick(this, "終日で登録", "All-day"));
        allDayCheck.setChecked(shift.allDay);

        form.addView(titleInput, formFieldParams());
        form.addView(startInput, formFieldParams());
        form.addView(endInput, formFieldParams());
        form.addView(allDayCheck, formFieldParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "シフトボタン編集", "Edit shift button"))
                .setView(form)
                .setPositiveButton(AppText.pick(this, "保存", "Save"), null)
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            String start = startInput.getText().toString().trim();
            String end = endInput.getText().toString().trim();
            boolean allDay = allDayCheck.isChecked();
            if (TextUtils.isEmpty(title)) {
                titleInput.setError(AppText.pick(this, "シフト名を入力してください", "Enter a shift name"));
                return;
            }
            if (!allDay && (parseTime(start) == null || parseTime(end) == null)) {
                Toast.makeText(this, AppText.pick(this,
                        "時刻は HH:mm で入力してください",
                        "Enter time as HH:mm"), Toast.LENGTH_LONG).show();
                return;
            }
            shiftDefinitions[index] = new ShiftDefinition(title, start, end, allDay);
            saveShiftDefinition(index);
            refreshScreen();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void launchShiftButtonExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, buildShiftButtonBackupFileName());
        startActivityForResult(intent, REQUEST_EXPORT_SHIFT_BUTTONS);
    }

    private void launchShiftButtonImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_SHIFT_BUTTONS);
    }

    private void exportShiftButtonsToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("output stream is null");
            }
            outputStream.write(exportShiftButtonsAsJson().getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Toast.makeText(this, AppText.pick(this,
                    "シフトボタンを保存しました",
                    "Shift buttons saved"), Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, AppText.pick(this,
                    "シフトボタンの保存に失敗しました",
                    "Failed to save shift buttons"), Toast.LENGTH_LONG).show();
        }
    }

    private void importShiftButtonsFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("input stream is null");
            }
            importShiftButtonsFromJson(readAllText(inputStream));
            saveAllShiftDefinitions();
            refreshScreen();
            Toast.makeText(this, AppText.pick(this,
                    "シフトボタンを読み込みました",
                    "Shift buttons loaded"), Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, AppText.pick(this,
                    "シフトボタンの読み込みに失敗しました",
                    "Failed to load shift buttons"), Toast.LENGTH_LONG).show();
        }
    }

    private String exportShiftButtonsAsJson() throws JSONException {
        JSONArray buttons = new JSONArray();
        for (ShiftDefinition shift : shiftDefinitions) {
            JSONObject item = new JSONObject();
            item.put("title", shift.title);
            item.put("start_time", shift.startTime);
            item.put("end_time", shift.endTime);
            item.put("all_day", shift.allDay);
            buttons.put(item);
        }

        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("buttons", buttons);
        return root.toString();
    }

    private void importShiftButtonsFromJson(String rawJson) throws JSONException {
        JSONObject root = new JSONObject(rawJson);
        JSONArray buttons = root.getJSONArray("buttons");
        int count = Math.min(buttons.length(), shiftDefinitions.length);
        for (int i = 0; i < count; i++) {
            JSONObject item = buttons.getJSONObject(i);
            ShiftDefinition current = shiftDefinitions[i];
            shiftDefinitions[i] = new ShiftDefinition(
                    item.optString("title", current.title),
                    item.optString("start_time", current.startTime),
                    item.optString("end_time", current.endTime),
                    item.optBoolean("all_day", current.allDay)
            );
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

    private void deleteShiftButtonEventsForSelectedDay() {
        if (!ensureSelectedCalendarWritable()) {
            return;
        }
        List<CalendarRepository.CalendarEvent> dayEvents =
                CalendarRepository.getEventsForDay(this, selectedCalendarId, selectedDayMillis);

        int deletedCount = 0;
        boolean failed = false;
        CalendarRepository.CalendarInfo calendarInfo = findCalendarInfo(selectedCalendarId);
        for (CalendarRepository.CalendarEvent event : dayEvents) {
            if (!isShiftButtonEvent(event)) {
                continue;
            }
            boolean deleted = CalendarRepository.deleteEvent(this, event, calendarInfo);
            if (deleted) {
                deletedCount++;
            } else {
                failed = true;
            }
        }

        long deletedDayMillis = selectedDayMillis;
        selectedDayMillis += DAY_IN_MILLIS;
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);
        refreshScreen();

        if (failed) {
            Toast.makeText(this, AppText.pick(this,
                    "一部のシフト削除に失敗しました",
                    "Failed to delete some shifts"), Toast.LENGTH_LONG).show();
            return;
        }
        if (deletedCount > 0) {
            Toast.makeText(this, AppText.pick(this,
                    formatShortDate(deletedDayMillis) + " のシフトを削除しました",
                    "Deleted shifts on " + formatShortDate(deletedDayMillis)), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, AppText.pick(this,
                    "削除するシフトがありません",
                    "No shift to delete"), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isShiftButtonEvent(CalendarRepository.CalendarEvent event) {
        String eventTitle = event.title == null ? "" : event.title.trim();
        if (TextUtils.isEmpty(eventTitle)) {
            return false;
        }
        for (ShiftDefinition shift : shiftDefinitions) {
            if (eventTitle.equals(shift.title.trim())) {
                return true;
            }
        }
        return false;
    }

    private void showCalendarPicker() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        List<String> customTargets = loadCustomSyncTargets();
        if (writableCalendars.isEmpty() && customTargets.isEmpty()) {
            Toast.makeText(this, AppText.pick(this,
                    "選択できるカレンダーがありません",
                    "No calendar can be selected"), Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> labels = new ArrayList<>();
        final int calendarCount = writableCalendars.size();
        int checkedItem = 0;
        for (int i = 0; i < writableCalendars.size(); i++) {
            CalendarRepository.CalendarInfo info = writableCalendars.get(i);
            labels.add(buildCalendarPickerLabel(info));
            if (info.id == selectedCalendarId) {
                checkedItem = i;
            }
        }
        for (String target : customTargets) {
            labels.add(buildCustomSyncTargetLabel(target));
            CalendarRepository.CalendarInfo resolved = resolveCustomSyncTarget(target);
            if (resolved != null && resolved.id == selectedCalendarId) {
                checkedItem = labels.size() - 1;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "同期先カレンダーを選択", "Select calendar"))
                .setSingleChoiceItems(labels.toArray(new String[0]), checkedItem, (dialog, which) -> {
                    if (which < calendarCount) {
                        selectedCalendarId = writableCalendars.get(which).id;
                        persistCalendarId(selectedCalendarId);
                        refreshMonthGrid();
                        refreshScreen();
                        dialog.dismiss();
                        return;
                    }

                    String target = customTargets.get(which - calendarCount);
                    CalendarRepository.CalendarInfo resolved = resolveCustomSyncTarget(target);
                    if (resolved == null) {
                        Toast.makeText(this, AppText.pick(this,
                                "その同期先はこの端末で見つかりません",
                                "That sync target was not found on this device"), Toast.LENGTH_LONG).show();
                        return;
                    }
                    selectedCalendarId = resolved.id;
                    persistCalendarId(selectedCalendarId);
                    refreshMonthGrid();
                    refreshScreen();
                    dialog.dismiss();
                })
                .setNegativeButton(AppText.pick(this, "閉じる", "Close"), null)
                .show();
    }

    private void showShiftDatePicker() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(selectedDayMillis);
        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(Calendar.YEAR, year);
                    calendar.set(Calendar.MONTH, month);
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    selectedDayMillis = startOfDay(calendar.getTimeInMillis());
                    visibleMonth.setTimeInMillis(selectedDayMillis);
                    resetToMonthStart(visibleMonth);
                    refreshScreen();
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private boolean ensureCalendarReady() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            Toast.makeText(this, AppText.pick(this,
                    "カレンダー権限を許可してください",
                    "Allow calendar permission"), Toast.LENGTH_LONG).show();
            return false;
        }
        if (writableCalendars.isEmpty()
                || selectedCalendarId < 0L
                || !containsCalendarId(selectedCalendarId)) {
            loadCalendars();
        }
        if (writableCalendars.isEmpty()
                || selectedCalendarId < 0L
                || !containsCalendarId(selectedCalendarId)) {
            Toast.makeText(this, AppText.pick(this,
                    "先に同期先カレンダーを選んでください",
                    "Select a calendar first"), Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private boolean ensureSelectedCalendarWritable() {
        if (!ensureCalendarReady()) {
            return false;
        }
        CalendarRepository.CalendarInfo info = findCalendarInfo(selectedCalendarId);
        if (info != null && info.canWrite()) {
            return true;
        }
        Toast.makeText(this, AppText.pick(this,
                "このカレンダーは閲覧のみです。シフトを追加・削除できません。",
                "This calendar is read only. Shifts cannot be added or deleted."), Toast.LENGTH_LONG).show();
        return false;
    }

    private void loadCalendars() {
        writableCalendars.clear();
        writableCalendars.addAll(CalendarRepository.getWritableCalendars(this));
        if (writableCalendars.isEmpty()) {
            selectedCalendarId = -1L;
            persistCalendarId(selectedCalendarId);
            return;
        }
        if (!containsCalendarId(selectedCalendarId)) {
            boolean restoredFromAllCalendars = false;
            if (selectedCalendarId >= 0L) {
                for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
                    if (info.id == selectedCalendarId) {
                        writableCalendars.add(info);
                        restoredFromAllCalendars = true;
                        break;
                    }
                }
            }
            if (!restoredFromAllCalendars) {
                selectedCalendarId = pickInitialCalendarId();
                persistCalendarId(selectedCalendarId);
            }
        }
        refreshMonthGrid();
    }

    private long pickInitialCalendarId() {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.isGoogleCalendar() && info.canWrite()) {
                return info.id;
            }
        }
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.canWrite()) {
                return info.id;
            }
        }
        return writableCalendars.get(0).id;
    }

    private boolean containsCalendarId(long calendarId) {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.id == calendarId) {
                return true;
            }
        }
        return false;
    }

    private CalendarRepository.CalendarInfo findCalendarInfo(long calendarId) {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.id == calendarId) {
                return info;
            }
        }
        return null;
    }

    private String getCalendarLabel() {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.id == selectedCalendarId) {
                return AppText.pick(this, "登録先: ", "Calendar: ") + buildCalendarPickerLabel(info);
            }
        }
        return AppText.pick(this, "登録先カレンダー未選択", "No calendar selected");
    }

    private String buildCalendarPickerLabel(CalendarRepository.CalendarInfo info) {
        String label = info.displayName + " / " + info.accountName;
        if (!info.canWrite()) {
            label += AppText.pick(this, " / 閲覧のみ", " / Read only");
        }
        return label;
    }

    private List<String> loadCustomSyncTargets() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return new ArrayList<>(preferences.getStringSet(CUSTOM_SYNC_TARGETS_KEY, new java.util.HashSet<>()));
    }

    private String buildCustomSyncTargetLabel(String target) {
        CalendarRepository.CalendarInfo info = resolveCustomSyncTargetNoSideEffect(target);
        if (info != null) {
            return buildCalendarPickerLabel(info);
        }
        return AppText.pick(this, "追加: ", "Added: ") + target;
    }

    private CalendarRepository.CalendarInfo resolveCustomSyncTarget(String target) {
        CalendarRepository.CalendarInfo info = resolveCustomSyncTargetNoSideEffect(target);
        if (info != null && !containsCalendarId(info.id)) {
            writableCalendars.add(info);
        }
        return info;
    }

    private CalendarRepository.CalendarInfo resolveCustomSyncTargetNoSideEffect(String target) {
        String normalizedTarget = normalizeSyncTarget(target);
        if (TextUtils.isEmpty(normalizedTarget)) {
            return null;
        }
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (matchesSyncTarget(info, normalizedTarget)) {
                return info;
            }
        }
        for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
            if (matchesSyncTarget(info, normalizedTarget)) {
                return info;
            }
        }
        return null;
    }

    private boolean matchesSyncTarget(CalendarRepository.CalendarInfo info, String target) {
        if (TextUtils.isEmpty(target)) {
            return false;
        }
        String normalizedTarget = normalizeSyncTargetForMatch(target);
        return matchesSyncTargetValue(normalizedTarget, info.accountName)
                || matchesSyncTargetValue(normalizedTarget, info.displayName)
                || matchesSyncTargetValue(normalizedTarget, info.ownerAccount)
                || matchesSyncTargetValue(normalizedTarget, info.displayName + "/" + info.accountName)
                || matchesSyncTargetValue(normalizedTarget, info.accountName + "/" + info.displayName)
                || matchesSyncTargetValue(normalizedTarget, info.displayName + "/" + info.ownerAccount)
                || matchesSyncTargetValue(normalizedTarget, info.ownerAccount + "/" + info.displayName)
                || matchesSyncTargetValue(normalizedTarget, AppText.pick(this, "追加: ", "Added: ") + info.accountName)
                || matchesSyncTargetValue(normalizedTarget, AppText.pick(this, "追加: ", "Added: ") + info.displayName);
    }

    private boolean matchesSyncTargetValue(String normalizedTarget, String value) {
        return !TextUtils.isEmpty(value)
                && normalizedTarget.equals(normalizeSyncTargetForMatch(value));
    }

    private String normalizeSyncTargetForMatch(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace('／', '/')
                .replaceAll("\\s*/\\s*", "/")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeSyncTarget(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private void loadShiftDefinitions() {
        SharedPreferences preferences = getSharedPreferences(SHIFT_PREFS_NAME, MODE_PRIVATE);
        for (int i = 0; i < shiftDefinitions.length; i++) {
            ShiftDefinition defaults = shiftDefinitions[i];
            shiftDefinitions[i] = new ShiftDefinition(
                    preferences.getString("title_" + i, defaults.title),
                    preferences.getString("start_" + i, defaults.startTime),
                    preferences.getString("end_" + i, defaults.endTime),
                    preferences.getBoolean("all_day_" + i, defaults.allDay)
            );
        }
    }

    private void saveShiftDefinition(int index) {
        ShiftDefinition shift = shiftDefinitions[index];
        getSharedPreferences(SHIFT_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString("title_" + index, shift.title)
                .putString("start_" + index, shift.startTime)
                .putString("end_" + index, shift.endTime)
                .putBoolean("all_day_" + index, shift.allDay)
                .apply();
    }

    private void saveAllShiftDefinitions() {
        SharedPreferences.Editor editor = getSharedPreferences(SHIFT_PREFS_NAME, MODE_PRIVATE).edit();
        for (int i = 0; i < shiftDefinitions.length; i++) {
            ShiftDefinition shift = shiftDefinitions[i];
            editor.putString("title_" + i, shift.title)
                    .putString("start_" + i, shift.startTime)
                    .putString("end_" + i, shift.endTime)
                    .putBoolean("all_day_" + i, shift.allDay);
        }
        editor.apply();
    }

    private void moveSelectedDay(int days) {
        selectedDayMillis += days * DAY_IN_MILLIS;
        visibleMonth.setTimeInMillis(selectedDayMillis);
        resetToMonthStart(visibleMonth);
        refreshScreen();
    }

    private void refreshMonthGrid() {
        if (monthCalendarAdapter == null || monthGridView == null || visibleMonth == null) {
            return;
        }
        monthDayCells.clear();
        monthEventsByDay.clear();
        long gridStartMillis = getGridStartMillis();
        int weekRows = getVisibleMonthWeekRows();
        updateMonthGridHeight(weekRows);
        if (hasCalendarPermissions() && selectedCalendarId >= 0L) {
            long gridEndMillis = gridStartMillis + (weekRows * 7L * DAY_IN_MILLIS);
            List<CalendarRepository.CalendarEvent> monthEvents =
                    CalendarRepository.getEventsForRange(this, selectedCalendarId, gridStartMillis, gridEndMillis);
            distributeEventsByDay(monthEvents, gridStartMillis, gridEndMillis, monthEventsByDay);
        }

        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(gridStartMillis);
        long todayStart = startOfDay(System.currentTimeMillis());
        int visibleDayCount = weekRows * 7;
        for (int i = 0; i < visibleDayCount; i++) {
            long dayStartMillis = dayCursor.getTimeInMillis();
            boolean isCurrentMonth = dayCursor.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH)
                    && dayCursor.get(Calendar.YEAR) == visibleMonth.get(Calendar.YEAR);
            List<CalendarRepository.CalendarEvent> dayEvents = monthEventsByDay.get(dayStartMillis);
            monthDayCells.add(new MonthDayCell(
                    dayStartMillis,
                    String.valueOf(dayCursor.get(Calendar.DAY_OF_MONTH)),
                    buildDaySummary(dayEvents),
                    dayCursor.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY,
                    dayCursor.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY,
                    JapaneseHolidayRepository.isHoliday(
                            dayCursor.get(Calendar.YEAR),
                            dayCursor.get(Calendar.MONTH) + 1,
                            dayCursor.get(Calendar.DAY_OF_MONTH)
                    ),
                    isCurrentMonth,
                    dayStartMillis == todayStart,
                    dayStartMillis == selectedDayMillis
            ));
            dayCursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        monthCalendarAdapter.notifyDataSetChanged();
        monthGridView.setSelection(0);
    }

    private void distributeEventsByDay(
            List<CalendarRepository.CalendarEvent> monthEvents,
            long gridStartMillis,
            long gridEndMillis,
            Map<Long, List<CalendarRepository.CalendarEvent>> target
    ) {
        for (CalendarRepository.CalendarEvent event : monthEvents) {
            long firstDay = Math.max(gridStartMillis, startOfDay(event.startMillis));
            long lastExclusive = Math.min(gridEndMillis, event.endMillis);
            if (lastExclusive <= firstDay) {
                lastExclusive = firstDay + DAY_IN_MILLIS;
            }
            for (long day = firstDay; day < lastExclusive; day += DAY_IN_MILLIS) {
                List<CalendarRepository.CalendarEvent> dayEvents = target.get(day);
                if (dayEvents == null) {
                    dayEvents = new ArrayList<>();
                    target.put(day, dayEvents);
                }
                if (dayEvents.size() < SHIFT_MONTH_EVENTS_PER_DAY) {
                    dayEvents.add(event);
                }
            }
        }
    }

    private String buildDaySummary(List<CalendarRepository.CalendarEvent> dayEvents) {
        if (dayEvents == null || dayEvents.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int visibleCount = Math.min(SHIFT_MONTH_EVENTS_PER_DAY, dayEvents.size());
        for (int i = 0; i < visibleCount; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(dayEvents.get(i).title);
        }
        return builder.toString();
    }

    private String buildDeleteEventLabel(CalendarRepository.CalendarEvent event) {
        String timeLabel;
        if (event.allDay) {
            timeLabel = AppText.pick(this, "終日", "All day");
        } else {
            java.text.DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
            timeLabel = timeFormat.format(new Date(event.startMillis))
                    + " - "
                    + timeFormat.format(new Date(event.endMillis));
        }
        return event.title + " / " + timeLabel;
    }

    private long getGridStartMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(visibleMonth.getTimeInMillis());
        resetToMonthStart(calendar);
        int offset = getMondayBasedDayOffset(calendar.get(Calendar.DAY_OF_WEEK));
        calendar.add(Calendar.DAY_OF_MONTH, -offset);
        return startOfDay(calendar.getTimeInMillis());
    }

    private int getVisibleMonthWeekRows() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(visibleMonth.getTimeInMillis());
        resetToMonthStart(calendar);
        int leadingDays = getMondayBasedDayOffset(calendar.get(Calendar.DAY_OF_WEEK));
        int daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int weekRows = (leadingDays + daysInMonth + 6) / 7;
        return Math.max(MIN_MONTH_WEEK_ROWS, Math.min(MAX_MONTH_WEEK_ROWS, weekRows));
    }

    private int getMondayBasedDayOffset(int dayOfWeek) {
        return (dayOfWeek + 5) % 7;
    }

    private void updateMonthGridHeight(int weekRows) {
        LinearLayout.LayoutParams params = monthGridParams(weekRows);
        if (monthGridView.getLayoutParams() == null || monthGridView.getLayoutParams().height != params.height) {
            monthGridView.setLayoutParams(params);
        }
    }

    private LinearLayout.LayoutParams monthGridParams(int weekRows) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp((weekRows * SHIFT_MONTH_CELL_HEIGHT_DP) + ((weekRows - 1) * SHIFT_MONTH_GRID_SPACING_DP))
        );
    }

    private long loadPersistedCalendarId() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_SELECTED_CALENDAR_ID, -1L);
    }

    private void persistCalendarId(long calendarId) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_SELECTED_CALENDAR_ID, calendarId)
                .apply();
    }

    private void installSystemAwareRootPadding(View root) {
        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();
        final int keyboardThreshold = dp(KEYBOARD_VISIBILITY_THRESHOLD_DP);
        final Rect visibleFrame = new Rect();
        final int[] rootLocation = new int[2];
        final int[] topSystemInset = {0};
        final int[] bottomSystemInset = {0};
        final int[] bottomImeInset = {0};

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                topSystemInset[0] = insets.getInsets(WindowInsets.Type.statusBars()).top;
                bottomSystemInset[0] = insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
                bottomImeInset[0] = insets.getInsets(WindowInsets.Type.ime()).bottom;
            } else {
                topSystemInset[0] = insets.getSystemWindowInsetTop();
                bottomSystemInset[0] = insets.getSystemWindowInsetBottom();
                bottomImeInset[0] = 0;
            }
            applyRootPadding(root, baseLeft, baseTop, baseRight, baseBottom,
                    topSystemInset[0], bottomSystemInset[0], bottomImeInset[0], keyboardThreshold);
            return insets;
        });
        root.requestApplyInsets();

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                root.getWindowVisibleDisplayFrame(visibleFrame);
                root.getLocationOnScreen(rootLocation);

                int rootBottom = rootLocation[1] + root.getHeight();
                int keyboardOverlap = Math.max(0, rootBottom - visibleFrame.bottom);
                int visibleTopInset = Math.max(0, visibleFrame.top - rootLocation[1]);
                if (topSystemInset[0] == 0 && visibleTopInset > 0) {
                    topSystemInset[0] = visibleTopInset;
                }
                int keyboardPadding = keyboardOverlap > keyboardThreshold ? keyboardOverlap : 0;
                applyRootPadding(root, baseLeft, baseTop, baseRight, baseBottom,
                        topSystemInset[0], bottomSystemInset[0], Math.max(bottomImeInset[0], keyboardPadding), keyboardThreshold);
            }
        });
    }

    private void applyRootPadding(
            View root,
            int baseLeft,
            int baseTop,
            int baseRight,
            int baseBottom,
            int topSystemInset,
            int bottomSystemInset,
            int bottomImeInset,
            int keyboardThreshold
    ) {
        int keyboardPadding = bottomImeInset > keyboardThreshold ? bottomImeInset : 0;
        int targetTop = baseTop + Math.max(0, topSystemInset);
        int targetBottom = baseBottom + Math.max(Math.max(0, bottomSystemInset), keyboardPadding);
        if (root.getPaddingLeft() != baseLeft
                || root.getPaddingTop() != targetTop
                || root.getPaddingRight() != baseRight
                || root.getPaddingBottom() != targetBottom) {
            root.setPadding(baseLeft, targetTop, baseRight, targetBottom);
        }
    }

    private boolean hasCalendarPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        return checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCalendarPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
        }, REQUEST_CALENDAR_PERMISSIONS);
    }

    private long buildDateTime(long dayMillis, int hour, int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(dayMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private int[] parseTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String[] parts = value.trim().split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return new int[]{hour, minute};
        } catch (NumberFormatException e) {
            return null;
        }
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

    private void resetToMonthStart(Calendar calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String formatDateTitle(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat(AppText.selectedDatePattern(this), AppText.displayLocale(this));
        return format.format(new Date(timeInMillis));
    }

    private String formatVisibleMonth(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat(AppText.visibleMonthPattern(this), AppText.displayLocale(this));
        return format.format(new Date(timeInMillis));
    }

    private String formatShortDate(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat("M/d", Locale.getDefault());
        return format.format(new Date(timeInMillis));
    }

    private String buildShiftButtonBackupFileName() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault());
        return "shift_buttons_" + format.format(new Date()) + ".json";
    }

    private Button buildButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedParams(float weight, boolean rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.weight = weight;
        if (rightMargin) {
            params.rightMargin = dp(8);
        }
        return params;
    }

    private LinearLayout.LayoutParams formFieldParams() {
        LinearLayout.LayoutParams params = matchWrapParams();
        params.bottomMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        if (value == 0) {
            return 0;
        }
        int pixels = Math.round(value * getResources().getDisplayMetrics().density);
        return value > 0 ? Math.max(1, pixels) : Math.min(-1, pixels);
    }

    private static final class ShiftDefinition {
        private final String title;
        private final String startTime;
        private final String endTime;
        private final boolean allDay;

        private ShiftDefinition(String title, String startTime, String endTime, boolean allDay) {
            this.title = title == null ? "" : title;
            this.startTime = startTime == null ? "" : startTime;
            this.endTime = endTime == null ? "" : endTime;
            this.allDay = allDay;
        }

        private String buttonLabel(Context context) {
            if (allDay) {
                return title + "\n" + AppText.pick(context, "終日", "All day");
            }
            return title + "\n" + startTime + "-" + endTime;
        }
    }
}
