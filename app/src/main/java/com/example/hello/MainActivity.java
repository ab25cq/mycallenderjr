package com.example.myhelloworld;

import android.Manifest;
import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.CalendarContract;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String TAG = "MyCalendarMain";
    private static final int REQUEST_CALENDAR_PERMISSIONS = 1001;
    private static final int REQUEST_EXPORT_TODOS = 2001;
    private static final int REQUEST_IMPORT_TODOS = 2002;
    private static final int REQUEST_PICK_WALLPAPER = 2003;
    private static final String PREFS_NAME = "calendar_app";
    private static final String SHIFT_PREFS_NAME = "shift_buttons";
    private static final String CUSTOM_SYNC_TARGETS_KEY = "custom_sync_targets";
    private static final String KEY_SELECTED_CALENDAR_ID = "selected_calendar_id";
    private static final String KEY_EXTRA_CALENDAR_IDS = "extra_calendar_ids";
    private static final String KEY_WALLPAPER_URI = "wallpaper_uri";
    private static final long DAY_IN_MILLIS = 24L * 60L * 60L * 1000L;
    private static final float MIN_FONT_SCALE = 0.90f;
    private static final int DEFAULT_MONTH_EVENTS_PER_DAY = 10;
    private static final int COMPACT_MONTH_EVENTS_PER_DAY = 10;
    private static final int NORMAL_MONTH_CELL_HEIGHT_DP = 148;
    private static final int COMPACT_MONTH_CELL_HEIGHT_DP = 128;
    private static final int MONTH_GRID_SPACING_DP = 2;
    private static final int MIN_MONTH_WEEK_ROWS = 5;
    private static final int MAX_MONTH_WEEK_ROWS = 6;
    private static final int COMPACT_SCREEN_HEIGHT_DP = 640;
    private static final int KEYBOARD_VISIBILITY_THRESHOLD_DP = 120;
    private static final float WALLPAPER_ALPHA = 0.90f;
    private static final int DEFAULT_BACKGROUND_COLOR = 0xFFFFF8F3;
    private static final int PRIMARY_TEXT_COLOR = 0xFF4A3035;
    private static final int BUTTON_BACKGROUND_COLOR = 0xFFF6CABB;
    private static final int BUTTON_BORDER_COLOR = 0xFFD9949F;

    private final List<CalendarRepository.CalendarInfo> writableCalendars = new ArrayList<>();
    private final List<CalendarRepository.CalendarEvent> eventsForSelectedDay = new ArrayList<>();
    private final List<LocalTodoRepository.LocalTodo> todosForSelectedDay = new ArrayList<>();
    private final List<ScheduleListAdapter.ScheduleListItem> selectedDayItems = new ArrayList<>();
    private final List<MonthDayCell> monthDayCells = new ArrayList<>();
    private final Map<Long, List<CalendarRepository.CalendarEvent>> monthEventsByDay = new HashMap<>();
    private final Handler monthLongPressHandler = new Handler(Looper.getMainLooper());
    private final Handler providerRefreshHandler = new Handler(Looper.getMainLooper());
    private final ContentObserver calendarContentObserver =
            new ContentObserver(providerRefreshHandler) {
                @Override
                public void onChange(boolean selfChange) {
                    scheduleProviderReloadFromContentChange();
                }
            };

    private TextView permissionView;
    private TextView monthLabelView;
    private TextView selectedDateView;
    private GridView monthGridView;
    private ImageView wallpaperView;
    private ListView scheduleListView;
    private ScheduleListAdapter scheduleListAdapter;
    private MonthCalendarAdapter monthCalendarAdapter;

    private long selectedDayMillis;
    private long selectedCalendarId = -1L;
    private long selectedEventId = -1L;
    private final LinkedHashSet<Long> extraCalendarIds = new LinkedHashSet<>();
    private Calendar visibleMonth;
    private float monthTouchDownX;
    private float monthTouchDownY;
    private int monthLongPressPosition = AdapterView.INVALID_POSITION;
    private boolean monthLongPressTriggered;
    private Runnable monthLongPressRunnable;
    private float scheduleTouchDownX;
    private float scheduleTouchDownY;
    private int scheduleTouchDownPosition = AdapterView.INVALID_POSITION;
    private boolean resetScheduleListPosition;
    private boolean mainCalendarDialogShowing;
    private boolean calendarEmailDialogShowing;
    private boolean calendarObserverRegistered;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(createStableDisplayContext(newBase));
    }

    private static Context createStableDisplayContext(Context context) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (configuration.fontScale < MIN_FONT_SCALE) {
            configuration.fontScale = MIN_FONT_SCALE;
        }

        int stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE;
        if (stableDensityDpi > 0
                && configuration.densityDpi > 0
                && configuration.densityDpi < stableDensityDpi) {
            configuration.densityDpi = stableDensityDpi;
        }

        return context.createConfigurationContext(configuration);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        boolean freshLaunch = savedInstanceState == null;

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
        extraCalendarIds.addAll(loadPersistedExtraCalendarIds());

        if (hasCalendarPermissions()) {
            loadCalendars();
            refreshMonthGrid();
            refreshEventList();
            promptForMainCalendar(freshLaunch);
        } else {
            showPermissionState();
            refreshMonthGrid();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCalendarPermissions()) {
            registerCalendarContentObserver();
            reloadCalendarDataFromProvider();
            maybePromptForMainCalendar();
        }
    }

    @Override
    protected void onPause() {
        unregisterCalendarContentObserver();
        super.onPause();
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
            registerCalendarContentObserver();
            reloadCalendarDataFromProvider();
            promptForMainCalendar(true);
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
            return;
        }

        if (requestCode == REQUEST_PICK_WALLPAPER) {
            saveWallpaperUri(uri);
            return;
        }

    }

    private void buildLayout() {
        int padding = dp(8);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));
        root.setPadding(padding, padding, padding, padding);

        permissionView = new TextView(this);
        permissionView.setTextSize(13);
        permissionView.setTextColor(PRIMARY_TEXT_COLOR);
        permissionView.setPadding(0, 0, 0, dp(8));

        LinearLayout monthBar = new LinearLayout(this);
        monthBar.setOrientation(LinearLayout.HORIZONTAL);
        monthBar.setGravity(Gravity.CENTER_VERTICAL);

        Button previousMonthButton = buildCompactActionButton("＜");
        previousMonthButton.setOnClickListener(v -> shiftMonth(-1));

        monthLabelView = new TextView(this);
        monthLabelView.setGravity(Gravity.CENTER);
        monthLabelView.setTextSize(18);
        monthLabelView.setTextColor(PRIMARY_TEXT_COLOR);
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
        controlBar.setPadding(0, dp(2), 0, 0);

        Button calendarButton = buildTightActionButton(AppText.pick(this, "表示切替", "Display"));
        calendarButton.setOnClickListener(v -> showCalendarPicker());

        Button refreshButton = buildTightActionButton(AppText.pick(this, "更新", "Update"));
        refreshButton.setOnClickListener(v -> {
            try {
                refreshAllData();
            } catch (RuntimeException e) {
                Log.w(TAG, "refresh button failed", e);
            }
        });

        Button wallpaperButton = buildTightActionButton(AppText.pick(this, "壁紙", "Wallpaper"));
        wallpaperButton.setOnClickListener(v -> showWallpaperMenu());

        controlBar.addView(calendarButton, createTightWeightedButtonLayoutParams());
        controlBar.addView(refreshButton, createTightWeightedButtonLayoutParams());
        controlBar.addView(wallpaperButton, createWeightedButtonLayoutParamsWithoutMargin());

        GridLayout weekdayHeader = new GridLayout(this);
        weekdayHeader.setColumnCount(7);
        weekdayHeader.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        String[] weekLabels = AppText.weekLabels(this);
        for (String weekLabel : weekLabels) {
            TextView labelView = new TextView(this);
            labelView.setText(weekLabel);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextSize(10);
            labelView.setTextColor(PRIMARY_TEXT_COLOR);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setGravity(Gravity.FILL_HORIZONTAL);
            labelView.setLayoutParams(params);
            weekdayHeader.addView(labelView);
        }

        monthGridView = new GridView(this);
        monthGridView.setNumColumns(7);
        monthGridView.setHorizontalSpacing(dp(MONTH_GRID_SPACING_DP));
        monthGridView.setVerticalSpacing(dp(MONTH_GRID_SPACING_DP));
        monthGridView.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        monthGridView.setGravity(Gravity.CENTER);
        monthGridView.setVerticalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            monthGridView.setNestedScrollingEnabled(false);
        }
        monthGridView.setSelector(android.R.color.transparent);
        monthCalendarAdapter = new MonthCalendarAdapter(this, monthDayCells);
        monthCalendarAdapter.setWallpaperEnabled(hasPersistedWallpaper());
        monthGridView.setAdapter(monthCalendarAdapter);
        LinearLayout.LayoutParams monthGridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(calculateMonthGridHeightDp(MIN_MONTH_WEEK_ROWS, NORMAL_MONTH_CELL_HEIGHT_DP))
        );
        monthGridParams.topMargin = dp(4);
        monthGridView.setLayoutParams(monthGridParams);
        monthGridView.setOnItemClickListener((parent, view, position, id) -> {
            MonthDayCell cell = monthDayCells.get(position);
            selectedDayMillis = cell.dayStartMillis;
            selectedEventId = -1L;
            resetScheduleListPosition = true;
            visibleMonth.setTimeInMillis(cell.dayStartMillis);
            resetToMonthStart(visibleMonth);
            refreshAllData();
        });
        monthGridView.setOnTouchListener(this::handleMonthGridTouch);

        selectedDateView = new TextView(this);
        selectedDateView.setTextSize(13);
        selectedDateView.setTextColor(PRIMARY_TEXT_COLOR);
        selectedDateView.setTypeface(selectedDateView.getTypeface(), android.graphics.Typeface.BOLD);
        selectedDateView.setPadding(0, dp(4), 0, dp(2));

        FrameLayout listContainer = new FrameLayout(this);
        LinearLayout.LayoutParams listContainerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
        );
        listContainerParams.weight = 1f;
        listContainer.setLayoutParams(listContainerParams);

        scheduleListView = new ListView(this);
        scheduleListView.setDividerHeight(dp(2));
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
        actionBar.setPadding(0, dp(2), 0, 0);

        Button todoButton = buildTightActionButton("TODO");
        todoButton.setOnClickListener(v -> startActivity(new Intent(this, TodoActivity.class)));

        Button diaryButton = buildTightActionButton(AppText.pick(this, "日記", "Diary"));
        diaryButton.setOnClickListener(v -> startActivity(new Intent(this, DiaryActivity.class)));

        Button todayButton = buildTightActionButton(AppText.pick(this, "本日", "Today"));
        todayButton.setOnClickListener(v -> jumpToToday());

        Button shiftButton = buildTightActionButton(AppText.pick(this, "シフト入力", "Shift"));
        shiftButton.setOnClickListener(v -> startActivity(new Intent(this, ShiftActivity.class)));

        actionBar.addView(todoButton, createTightWeightedButtonLayoutParams());
        actionBar.addView(diaryButton, createTightWeightedButtonLayoutParams());
        actionBar.addView(shiftButton, createTightWeightedButtonLayoutParams());
        actionBar.addView(todayButton, createWeightedButtonLayoutParamsWithoutMargin());

        root.addView(permissionView);
        root.addView(monthBar);
        root.addView(weekdayHeader);
        root.addView(monthGridView);
        root.addView(actionBar);
        root.addView(controlBar);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout screenRoot = new FrameLayout(this);
        screenRoot.setBackgroundColor(DEFAULT_BACKGROUND_COLOR);
        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setAlpha(WALLPAPER_ALPHA);
        wallpaperView.setVisibility(View.GONE);
        screenRoot.addView(wallpaperView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        screenRoot.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(screenRoot);
        applyPersistedWallpaper();
        installSystemAwareRootPadding(scrollView);
    }

    private void refreshAllData() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        try {
            requestGoogleCalendarProviderSync();
            reloadCalendarDataFromProvider();
        } catch (RuntimeException e) {
            Log.w(TAG, "provider refresh failed", e);
            Toast.makeText(this, AppText.pick(this,
                    "端末カレンダーの更新に失敗しました",
                    "Failed to update device calendars"), Toast.LENGTH_LONG).show();
        }
    }

    private void requestGoogleCalendarProviderSync() {
        Set<String> requestedAccounts = new HashSet<>();
        for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
            if (info == null
                    || !"com.google".equals(info.accountType)
                    || TextUtils.isEmpty(info.accountName)
                    || !requestedAccounts.add(info.accountName)) {
                continue;
            }
            try {
                Bundle extras = new Bundle();
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
                ContentResolver.requestSync(
                        new Account(info.accountName, info.accountType),
                        CalendarContract.AUTHORITY,
                        extras
                );
            } catch (RuntimeException e) {
                Log.w(TAG, "failed to request Google calendar provider sync", e);
            }
        }
    }

    private void reloadCalendarDataFromProvider() {
        clearProviderBackedDisplayState();
        selectedCalendarId = loadPersistedCalendarId();
        extraCalendarIds.addAll(loadPersistedExtraCalendarIds());
        loadCalendars();
        refreshMonthGrid();
        refreshEventList();
    }

    private void clearProviderBackedDisplayState() {
        writableCalendars.clear();
        extraCalendarIds.clear();
        eventsForSelectedDay.clear();
        todosForSelectedDay.clear();
        selectedDayItems.clear();
        monthEventsByDay.clear();
        monthDayCells.clear();
        selectedEventId = -1L;
        resetScheduleListPosition = true;
        if (monthCalendarAdapter != null) {
            monthCalendarAdapter.notifyDataSetChanged();
        }
        if (scheduleListAdapter != null) {
            scheduleListAdapter.notifyDataSetChanged();
        }
    }

    private void registerCalendarContentObserver() {
        if (calendarObserverRegistered || !hasCalendarPermissions()) {
            return;
        }
        try {
            getContentResolver().registerContentObserver(
                    CalendarContract.Events.CONTENT_URI,
                    true,
                    calendarContentObserver
            );
            getContentResolver().registerContentObserver(
                    CalendarContract.Instances.CONTENT_URI,
                    true,
                    calendarContentObserver
            );
            getContentResolver().registerContentObserver(
                    CalendarContract.Calendars.CONTENT_URI,
                    true,
                    calendarContentObserver
            );
            calendarObserverRegistered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to register calendar content observer", e);
        }
    }

    private void unregisterCalendarContentObserver() {
        if (!calendarObserverRegistered) {
            return;
        }
        providerRefreshHandler.removeCallbacks(providerContentReloadRunnable);
        try {
            getContentResolver().unregisterContentObserver(calendarContentObserver);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to unregister calendar content observer", e);
        } finally {
            calendarObserverRegistered = false;
        }
    }

    private void scheduleProviderReloadFromContentChange() {
        providerRefreshHandler.removeCallbacks(providerContentReloadRunnable);
        providerRefreshHandler.postDelayed(providerContentReloadRunnable, 500L);
    }

    private final Runnable providerContentReloadRunnable = this::reloadCalendarDataFromProviderSafely;

    private void reloadCalendarDataFromProviderSafely() {
        if (!isActivityAlive() || !hasCalendarPermissions()) {
            return;
        }
        try {
            reloadCalendarDataFromProvider();
        } catch (RuntimeException e) {
            Log.w(TAG, "provider change reload failed", e);
        }
    }

    private boolean isActivityAlive() {
        if (isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !isDestroyed();
    }

    private void showPermissionState() {
        permissionView.setVisibility(View.VISIBLE);
        permissionView.setText(AppText.pick(this,
                "カレンダー権限を許可すると予定を表示して同期できます。",
                "Allow calendar permission to show and sync events."));
        requestCalendarPermissions();
    }

    private void showCalendarUnavailableState() {
        permissionView.setVisibility(View.VISIBLE);
        permissionView.setText(AppText.pick(this,
                "表示できるカレンダーがありません。端末で Google カレンダー同期を有効にしてください。",
                "No visible calendar is available. Enable Google Calendar sync on this device."));
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

        if (!isSelectedCalendarAvailable()) {
            selectedCalendarId = -1L;
            persistCalendarId(selectedCalendarId);
        }

        // Ensure extra calendars are accessible; remove stale IDs
        for (long extraId : new ArrayList<>(extraCalendarIds)) {
            if (!containsCalendarId(extraId)) {
                boolean found = false;
                for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
                    if (info.id == extraId) {
                        writableCalendars.add(info);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    extraCalendarIds.remove(extraId);
                }
            }
        }
    }

    private void maybePromptForMainCalendar() {
        promptForMainCalendar(false);
    }

    private void promptForMainCalendar(boolean force) {
        if (!hasCalendarPermissions()) {
            return;
        }
        if (CalendarRepository.getAllCalendars(this).isEmpty()
                && loadCustomSyncTargets().isEmpty()) {
            showCalendarEmailAddDialog(true);
            return;
        }
        if (mainCalendarDialogShowing) {
            return;
        }
        if (!force && isSelectedCalendarAvailable()) {
            return;
        }
        if (writableCalendars.isEmpty()) {
            return;
        }
        showSelectMainCalendarDialog();
    }

    private void showSelectMainCalendarDialog() {
        List<CalendarRepository.CalendarInfo> candidates = new ArrayList<>(writableCalendars);
        if (candidates.isEmpty()) {
            return;
        }
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = buildCalendarPickerLabel(candidates.get(i));
        }

        mainCalendarDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "メインカレンダーを選択", "Select Main Calendar"))
                .setItems(labels, (dialog, which) -> {
                    CalendarRepository.CalendarInfo info = candidates.get(which);
                    selectedCalendarId = info.id;
                    persistCalendarId(selectedCalendarId);
                    loadCalendars();
                    refreshMonthGrid();
                    refreshEventList();
                    Toast.makeText(this, AppText.pick(this,
                            "メインカレンダーを設定しました",
                            "Main calendar set"), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(AppText.pick(this, "後で", "Later"), null)
                .setOnDismissListener(dialog -> mainCalendarDialogShowing = false)
                .show();
    }

    private boolean isSelectedCalendarAvailable() {
        if (selectedCalendarId < 0L) {
            return false;
        }
        CalendarRepository.CalendarInfo info = findCalendarInfo(selectedCalendarId);
        return info != null;
    }

    private void refreshMonthGrid() {
        monthLabelView.setText(formatVisibleMonth(visibleMonth.getTimeInMillis()));

        monthDayCells.clear();
        long gridStartMillis = getGridStartMillis();
        int weekRows = getVisibleMonthWeekRows();
        int maxMonthEventsPerDay = getMaxMonthEventsPerDay(weekRows);
        int monthCellHeightDp = getMonthCellHeightDp(maxMonthEventsPerDay);
        updateMonthGridDisplay(weekRows, monthCellHeightDp, maxMonthEventsPerDay);
        monthEventsByDay.clear();

        if (hasCalendarPermissions() && selectedCalendarId >= 0L) {
            long gridEndMillis = gridStartMillis + (weekRows * 7L * DAY_IN_MILLIS);
            List<CalendarRepository.CalendarEvent> monthEvents = new ArrayList<>();
            for (long calId : getDisplayCalendarIds()) {
                monthEvents.addAll(CalendarRepository.getEventsForRange(this, calId, gridStartMillis, gridEndMillis));
            }
            distributeEventsByDay(monthEvents, gridStartMillis, gridEndMillis, maxMonthEventsPerDay, monthEventsByDay);
        }

        Calendar dayCursor = Calendar.getInstance();
        dayCursor.setTimeInMillis(gridStartMillis);
        long todayStart = startOfDay(System.currentTimeMillis());
        Set<String> shiftButtonTitles = loadShiftButtonTitles();

        int visibleDayCount = weekRows * 7;
        for (int i = 0; i < visibleDayCount; i++) {
            long dayStartMillis = dayCursor.getTimeInMillis();
            boolean isCurrentMonth = dayCursor.get(Calendar.MONTH) == visibleMonth.get(Calendar.MONTH)
                    && dayCursor.get(Calendar.YEAR) == visibleMonth.get(Calendar.YEAR);
            List<CalendarRepository.CalendarEvent> dayEvents = monthEventsByDay.get(dayStartMillis);
            monthDayCells.add(new MonthDayCell(
                    dayStartMillis,
                    String.valueOf(dayCursor.get(Calendar.DAY_OF_MONTH)),
                    buildDaySummary(dayStartMillis, dayEvents, maxMonthEventsPerDay, shiftButtonTitles),
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
        monthGridView.post(() -> monthGridView.setSelection(0));
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

    private int getMaxMonthEventsPerDay(int weekRows) {
        return getScreenHeightDp() <= COMPACT_SCREEN_HEIGHT_DP
                ? COMPACT_MONTH_EVENTS_PER_DAY
                : DEFAULT_MONTH_EVENTS_PER_DAY;
    }

    private int getMonthCellHeightDp(int maxMonthEventsPerDay) {
        return maxMonthEventsPerDay <= COMPACT_MONTH_EVENTS_PER_DAY
                ? COMPACT_MONTH_CELL_HEIGHT_DP
                : NORMAL_MONTH_CELL_HEIGHT_DP;
    }

    private void updateMonthGridDisplay(int weekRows, int cellHeightDp, int maxMonthEventsPerDay) {
        monthCalendarAdapter.setDisplayDensity(cellHeightDp, maxMonthEventsPerDay);

        ViewGroup.LayoutParams rawParams = monthGridView.getLayoutParams();
        if (rawParams != null) {
            int targetHeight = dp(calculateMonthGridHeightDp(weekRows, cellHeightDp));
            if (rawParams.height != targetHeight) {
                rawParams.height = targetHeight;
                monthGridView.setLayoutParams(rawParams);
            }
        }
    }

    private int calculateMonthGridHeightDp(int weekRows, int cellHeightDp) {
        return (weekRows * cellHeightDp) + ((weekRows - 1) * MONTH_GRID_SPACING_DP);
    }

    private int getScreenHeightDp() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(displayMetrics.heightPixels / displayMetrics.density);
    }

    private void distributeEventsByDay(
            List<CalendarRepository.CalendarEvent> monthEvents,
            long gridStartMillis,
            long gridEndMillis,
            int maxMonthEventsPerDay,
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
                    if (dayEvents.size() < maxMonthEventsPerDay) {
                        dayEvents.add(event);
                    }
                }
                cursor += DAY_IN_MILLIS;
            }
        }
    }

    private CharSequence buildDaySummary(
            long dayStartMillis,
            List<CalendarRepository.CalendarEvent> dayEvents,
            int maxMonthEventsPerDay,
            Set<String> shiftButtonTitles
    ) {
        if (dayEvents == null || dayEvents.isEmpty()) {
            return "";
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        int visibleCount = Math.min(maxMonthEventsPerDay, dayEvents.size());
        for (int i = 0; i < visibleCount; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            CalendarRepository.CalendarEvent event = dayEvents.get(i);
            int start = builder.length();
            builder.append(event.title);
            int end = builder.length();
            if (isShiftButtonTitle(event.title, shiftButtonTitles)) {
                builder.setSpan(new BackgroundColorSpan(0xFF2563EB), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new ForegroundColorSpan(0xFFFFFFFF), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return builder;
    }

    private boolean isShiftButtonTitle(String title, Set<String> shiftButtonTitles) {
        return !TextUtils.isEmpty(title) && shiftButtonTitles.contains(title.trim());
    }

    private Set<String> loadShiftButtonTitles() {
        String[] defaults = {
                "早番", "日勤", "遅番", "準夜", "夜勤",
                "明け", "休み", "有休", "公休", "希望休",
                "研修", "出張", "会議", "欠勤", "その他"
        };
        SharedPreferences preferences = getSharedPreferences(SHIFT_PREFS_NAME, MODE_PRIVATE);
        Set<String> titles = new HashSet<>();
        for (int i = 0; i < defaults.length; i++) {
            String title = preferences.getString("title_" + i, defaults[i]);
            if (!TextUtils.isEmpty(title)) {
                titles.add(title.trim());
            }
        }
        return titles;
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
        // The selected-day list should always reflect the latest provider data,
        // even if the month grid still has older cached cell summaries.
        for (long calId : getDisplayCalendarIds()) {
            eventsForSelectedDay.addAll(CalendarRepository.getEventsForDay(this, calId, selectedDayMillis));
        }
        todosForSelectedDay.clear();
        todosForSelectedDay.addAll(LocalTodoRepository.getTodos(this));

        List<CalendarRepository.CalendarEvent> cachedEvents = monthEventsByDay.get(selectedDayMillis);
        Log.d(TAG, "refreshEventList day=" + selectedDayMillis
                + " calendarId=" + selectedCalendarId
                + " providerCount=" + eventsForSelectedDay.size()
                + " cachedCount=" + (cachedEvents == null ? 0 : cachedEvents.size())
                + " providerEvents=" + summarizeEvents(eventsForSelectedDay)
                + " cachedEvents=" + summarizeEvents(cachedEvents));

        if (!containsEventId(selectedEventId)) {
            selectedEventId = eventsForSelectedDay.isEmpty() ? -1L : eventsForSelectedDay.get(0).id;
        }

        refreshSelectedDayItems();
    }

    private void refreshSelectedDayItems() {
        selectedDayItems.clear();

        selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.section(AppText.pick(this, "予定", "Events")));
        if (eventsForSelectedDay.isEmpty()) {
            selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.message(AppText.pick(this,
                    "カレンダー予定はありません。",
                    "No calendar events.")));
        } else {
            for (CalendarRepository.CalendarEvent event : eventsForSelectedDay) {
                selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.event(this, event));
            }
        }

        selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.section("TODO"));
        if (todosForSelectedDay.isEmpty()) {
            selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.message(AppText.pick(this,
                    "TODO はありません。",
                    "No TODO items.")));
        } else {
            for (LocalTodoRepository.LocalTodo todo : todosForSelectedDay) {
                selectedDayItems.add(ScheduleListAdapter.ScheduleListItem.todo(todo));
            }
        }

        scheduleListAdapter.setSelectedEventId(selectedEventId);
        scheduleListAdapter.replaceItems(selectedDayItems);
        if (resetScheduleListPosition && scheduleListView != null) {
            scheduleListView.post(() -> scheduleListView.setSelection(0));
            resetScheduleListPosition = false;
        }
    }

    private String summarizeEvents(List<CalendarRepository.CalendarEvent> events) {
        if (events == null || events.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(events.size(), 8);
        for (int i = 0; i < limit; i++) {
            CalendarRepository.CalendarEvent event = events.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("{id=").append(event.id)
                    .append(",eventId=").append(event.eventId)
                    .append(",title=").append(event.title)
                    .append("}");
        }
        if (events.size() > limit) {
            builder.append(", ... total=").append(events.size());
        }
        builder.append(']');
        return builder.toString();
    }

    private void showCalendarPicker() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            return;
        }
        showCalendarPickerAfterSyncRefresh();
    }

    private void showCalendarPickerAfterSyncRefresh() {
        loadCalendars();
        refreshMonthGrid();
        refreshEventList();
        List<CalendarRepository.CalendarInfo> freshCalendars = CalendarRepository.getAllCalendars(this);
        if (writableCalendars.isEmpty() && freshCalendars.isEmpty() && loadCustomSyncTargets().isEmpty()) {
            showCalendarEmailAddDialog(true);
            return;
        }

        List<String> customTargets = loadCustomSyncTargets();
        List<String> labels = new ArrayList<>();
        List<Long> calendarIds = new ArrayList<>();
        Set<Long> listedCalendarIds = new HashSet<>();

        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            String label = buildCalendarPickerLabel(info);
            if (info.id == selectedCalendarId) {
                label = AppText.pick(this, "(メイン) ", "(Main) ") + label;
            }
            labels.add(label);
            calendarIds.add(info.id);
            listedCalendarIds.add(info.id);
        }

        for (CalendarRepository.CalendarInfo info : freshCalendars) {
            if (!listedCalendarIds.add(info.id)) {
                continue;
            }
            String label = buildCalendarPickerLabel(info);
            if (info.id == selectedCalendarId) {
                label = AppText.pick(this, "(メイン) ", "(Main) ") + label;
            }
            labels.add(label);
            calendarIds.add(info.id);
        }

        for (String target : customTargets) {
            CalendarRepository.CalendarInfo resolved = resolveCustomSyncTarget(target);
            if (resolved == null) {
                labels.add(buildCustomSyncTargetLabel(target));
                calendarIds.add(-1L);
            } else if (listedCalendarIds.add(resolved.id)) {
                String label = buildCalendarPickerLabel(resolved);
                if (resolved.id == selectedCalendarId) {
                    label = AppText.pick(this, "(メイン) ", "(Main) ") + label;
                }
                labels.add(label);
                calendarIds.add(resolved.id);
            }
        }

        boolean[] checked = new boolean[labels.size()];
        for (int i = 0; i < calendarIds.size(); i++) {
            long id = calendarIds.get(i);
            checked[i] = (id == selectedCalendarId) || extraCalendarIds.contains(id);
        }
        boolean[] current = checked.clone();

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "表示するカレンダーを選択", "Select Calendars to Display"))
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, which, isChecked) -> {
                    long id = calendarIds.get(which);
                    if (id < 0L) {
                        Toast.makeText(this, AppText.pick(this,
                                "このカレンダーは端末で見つかりません",
                                "This calendar was not found on this device"), Toast.LENGTH_LONG).show();
                        ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                        current[which] = false;
                        return;
                    }
                    if (id == selectedCalendarId && !isChecked) {
                        Toast.makeText(this, AppText.pick(this,
                                "メインカレンダーは外せません",
                                "Cannot uncheck the main calendar"), Toast.LENGTH_SHORT).show();
                        ((AlertDialog) dialog).getListView().setItemChecked(which, true);
                        current[which] = true;
                        return;
                    }
                    current[which] = isChecked;
                })
                .setPositiveButton(AppText.pick(this, "確認", "OK"), (dialog, which) -> {
                    extraCalendarIds.clear();
                    for (int i = 0; i < calendarIds.size(); i++) {
                        long id = calendarIds.get(i);
                        if (id >= 0L && id != selectedCalendarId && current[i]) {
                            extraCalendarIds.add(id);
                        }
                    }
                    persistExtraCalendarIds();
                    loadCalendars();
                    refreshMonthGrid();
                    refreshEventList();
                })
                .setNeutralButton(AppText.pick(this, "カレンダーを追加", "Add Calendar"), (dialog, which) ->
                        showCalendarEmailAddDialog())
                .setNegativeButton(AppText.pick(this, "閉じる", "Close"), null)
                .show();
    }

    private void showCalendarManagerDialog() {
        if (!hasCalendarPermissions()) {
            requestCalendarPermissions();
            Toast.makeText(this, AppText.pick(this,
                    "カレンダーの権限を許可してから再度お試しください",
                    "Please grant calendar permission and try again"), Toast.LENGTH_LONG).show();
            return;
        }

        loadCalendars();
        List<CalendarRepository.CalendarInfo> freshCalendars = CalendarRepository.getAllCalendars(this);
        List<String> customTargets = loadCustomSyncTargets();

        if (freshCalendars.isEmpty() && customTargets.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle(AppText.pick(this, "カレンダーの追加", "Add Calendar"))
                    .setMessage(AppText.pick(this,
                            "端末で表示できるカレンダーがありません。",
                            "No calendars are available on this device."))
                    .setNeutralButton(AppText.pick(this, "カレンダーを追加", "Add Calendar"), (d, i) ->
                            showCalendarEmailAddDialog())
                    .setNegativeButton(AppText.pick(this, "閉じる", "Close"), null)
                    .show();
            return;
        }

        List<String> labelsList = new ArrayList<>();
        List<Long> resolvedIds = new ArrayList<>();
        Set<Long> listedCalendarIds = new HashSet<>();

        for (CalendarRepository.CalendarInfo info : freshCalendars) {
            labelsList.add(buildCalendarPickerLabel(info));
            resolvedIds.add(info.id);
            listedCalendarIds.add(info.id);
        }

        for (String target : customTargets) {
            CalendarRepository.CalendarInfo info = resolveCustomSyncTargetNoSideEffect(target);
            if (info != null) {
                if (listedCalendarIds.add(info.id)) {
                    labelsList.add(buildCalendarPickerLabel(info));
                    resolvedIds.add(info.id);
                }
            } else {
                labelsList.add(buildCustomSyncTargetLabel(target));
                resolvedIds.add(-1L);
            }
        }

        String[] labels = labelsList.toArray(new String[0]);
        boolean[] checked = new boolean[resolvedIds.size()];
        boolean[] current = new boolean[resolvedIds.size()];
        for (int i = 0; i < resolvedIds.size(); i++) {
            long id = resolvedIds.get(i);
            boolean isActive = id >= 0L && (id == selectedCalendarId || extraCalendarIds.contains(id));
            checked[i] = isActive;
            current[i] = isActive;
        }

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "カレンダーの追加・表示切替", "Calendar Management"))
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    long id = resolvedIds.get(which);
                    if (id < 0L) {
                        Toast.makeText(this, AppText.pick(this,
                                "このカレンダーは端末に同期されていません",
                                "This calendar is not synced to this device"), Toast.LENGTH_SHORT).show();
                        ((AlertDialog) dialog).getListView().setItemChecked(which, false);
                        current[which] = false;
                        return;
                    }
                    if (id == selectedCalendarId && !isChecked) {
                        Toast.makeText(this, AppText.pick(this,
                                "メインカレンダーは外せません",
                                "Cannot uncheck the main calendar"), Toast.LENGTH_SHORT).show();
                        ((AlertDialog) dialog).getListView().setItemChecked(which, true);
                        current[which] = true;
                        return;
                    }
                    current[which] = isChecked;
                })
                .setNeutralButton(AppText.pick(this, "カレンダーを追加", "Add Calendar"), (d, i) ->
                        showCalendarEmailAddDialog())
                .setPositiveButton(AppText.pick(this, "確認", "OK"), (dialog, which) -> {
                    for (int i = 0; i < resolvedIds.size(); i++) {
                        long id = resolvedIds.get(i);
                        if (id < 0L || id == selectedCalendarId) continue;
                        if (current[i]) {
                            extraCalendarIds.add(id);
                        } else {
                            extraCalendarIds.remove(id);
                        }
                    }
                    persistExtraCalendarIds();
                    loadCalendars();
                    refreshMonthGrid();
                    refreshEventList();
                })
                .setNegativeButton(AppText.pick(this, "閉じる", "Close"), null)
                .show();
    }

    private void showCalendarEmailAddDialog() {
        showCalendarEmailAddDialog(false);
    }

    private void showCalendarEmailAddDialog(boolean required) {
        if (calendarEmailDialogShowing) {
            return;
        }
        EditText input = new EditText(this);
        input.setHint(AppText.pick(this, "例: friend@gmail.com", "Example: friend@gmail.com"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        int padding = dp(16);
        input.setPadding(padding, padding, padding, padding);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "カレンダーを追加", "Add Calendar"))
                .setMessage(AppText.pick(this,
                        required
                                ? "同期するカレンダーがありません。カレンダーのメールアドレスを入力してください。"
                                : "追加するカレンダーのメールアドレスを入力してください。\n共有を許可されたカレンダーは共有元のメールアドレスを入力してください。",
                        required
                                ? "No calendar is available to sync. Enter the calendar email address."
                                : "Enter the email address of the calendar to add.\nFor calendars shared with you, enter the email address of the owner who shared it."))
                .setView(input)
                .setPositiveButton(AppText.pick(this, "追加", "Add"), null);
        if (!required) {
            builder.setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null);
        }

        AlertDialog dialog = builder.create();
        dialog.setCancelable(!required);
        dialog.setCanceledOnTouchOutside(!required);
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String email = normalizeSyncTargetForMatch(input.getText().toString());
                    if (TextUtils.isEmpty(email) || !email.contains("@")) {
                        Toast.makeText(this, AppText.pick(this,
                                "正しいメールアドレスを入力してください",
                                "Enter a valid email address"),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    saveCustomSyncTarget(email);
                    dialog.dismiss();
                    reloadCalendarDataFromProviderSafely();
                    Toast.makeText(this, AppText.pick(this,
                            "メールアドレスを追加しました。同期後、表示切替でカレンダーを選んでください。",
                            "Email added. After sync, select the calendar under Display."),
                            Toast.LENGTH_LONG).show();
                }));
        dialog.setOnDismissListener(ignored -> calendarEmailDialogShowing = false);
        calendarEmailDialogShowing = true;
        dialog.show();
    }

    private void saveCustomSyncTarget(String target) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> targets = new HashSet<>(preferences.getStringSet(CUSTOM_SYNC_TARGETS_KEY, new HashSet<>()));
        targets.add(target);
        preferences.edit().putStringSet(CUSTOM_SYNC_TARGETS_KEY, targets).apply();
    }

    private List<String> loadCustomSyncTargets() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> stored = preferences.getStringSet(CUSTOM_SYNC_TARGETS_KEY, new HashSet<>());
        return new ArrayList<>(stored);
    }

    private String buildCustomSyncTargetLabel(String target) {
        CalendarRepository.CalendarInfo info = resolveCustomSyncTargetNoSideEffect(target);
        if (info != null) {
            return buildCalendarPickerLabel(info);
        }
        if (target != null && target.startsWith("id:")) {
            String rest = target.substring(3);
            int colonIdx = rest.indexOf(':');
            String email = colonIdx >= 0 ? rest.substring(colonIdx + 1) : null;
            if (!TextUtils.isEmpty(email)) {
                return AppText.pick(this, "追加: ", "Added: ") + email
                        + AppText.pick(this, " (未同期)", " (not synced)");
            }
            return AppText.pick(this, "追加済み (端末に未同期)", "Added (not synced to device)");
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
        if (target != null && target.startsWith("id:")) {
            String rest = target.substring(3);
            int colonIdx = rest.indexOf(':');
            String idStr = colonIdx >= 0 ? rest.substring(0, colonIdx) : rest;
            String emailFallback = colonIdx >= 0 ? rest.substring(colonIdx + 1) : null;
            try {
                long calendarId = Long.parseLong(idStr);
                for (CalendarRepository.CalendarInfo info : writableCalendars) {
                    if (info.id == calendarId) return info;
                }
                for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
                    if (info.id == calendarId) return info;
                }
            } catch (NumberFormatException ignored) {
            }
            // IDで見つからない場合、メアドフォールバック（別端末対応）
            if (!TextUtils.isEmpty(emailFallback)) {
                for (CalendarRepository.CalendarInfo info : writableCalendars) {
                    if (matchesSyncTarget(info, emailFallback)) return info;
                }
                for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
                    if (matchesSyncTarget(info, emailFallback)) return info;
                }
            }
            return null;
        }
        // Legacy: string matching for entries saved before the id: format
        String normalizedTarget = target == null ? "" : target.trim();
        if (TextUtils.isEmpty(normalizedTarget)) return null;
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (matchesSyncTarget(info, normalizedTarget)) return info;
        }
        for (CalendarRepository.CalendarInfo info : CalendarRepository.getAllCalendars(this)) {
            if (matchesSyncTarget(info, normalizedTarget)) return info;
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

    private String buildCalendarPickerLabel(CalendarRepository.CalendarInfo info) {
        String label = info.displayName + " / " + info.accountName;
        if (!info.canWrite()) {
            label += AppText.pick(this, " / 閲覧のみ", " / Read only");
        }
        return label;
    }

    private void confirmHolidayImport() {
        if (!ensureSelectedCalendarWritable()) {
            return;
        }
        int year = visibleMonth.get(Calendar.YEAR);
        List<JapaneseHolidayRepository.Holiday> holidays = JapaneseHolidayRepository.getHolidaysForYear(year);
        if (holidays.isEmpty()) {
            Toast.makeText(this, AppText.pick(this,
                    year + "年の祝日データはまだ入っていません",
                    "Holiday data for " + year + " is not available yet"), Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "祝日を入力", "Add holidays"))
                .setMessage(AppText.pick(this,
                        year + "年の祝日を終日予定として追加します。既に同じ日の同じ祝日名がある場合は追加しません。",
                        "Add " + year + " holidays as all-day events. Existing holidays with the same date and title are skipped."))
                .setPositiveButton(AppText.pick(this, "入力", "Add"), (dialog, which) -> importHolidaysForYear(year, holidays))
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void importHolidaysForYear(int year, List<JapaneseHolidayRepository.Holiday> holidays) {
        long rangeStartMillis = buildLocalDateMillis(year, 1, 1);
        long rangeEndMillis = buildLocalDateMillis(year + 1, 1, 1);
        List<CalendarRepository.CalendarEvent> existingEvents = new ArrayList<>();
        for (long calId : getDisplayCalendarIds()) {
            existingEvents.addAll(CalendarRepository.getEventsForRange(this, calId, rangeStartMillis, rangeEndMillis));
        }

        int insertedCount = 0;
        for (JapaneseHolidayRepository.Holiday holiday : holidays) {
            long startMillis = buildLocalDateMillis(year, holiday.month, holiday.day);
            if (containsHolidayEvent(existingEvents, startMillis, holiday.title)) {
                continue;
            }
            long eventId = CalendarRepository.insertEvent(
                    this,
                    selectedCalendarId,
                    holiday.title,
                    AppText.pick(this, "祝日自動入力", "Holiday auto entry"),
                    startMillis,
                    startMillis + DAY_IN_MILLIS,
                    true
            );
            if (eventId >= 0L) {
                insertedCount++;
                existingEvents.add(new CalendarRepository.CalendarEvent(
                        eventId,
                        eventId,
                        selectedCalendarId,
                        holiday.title,
                        AppText.pick(this, "祝日自動入力", "Holiday auto entry"),
                        startMillis,
                        startMillis + DAY_IN_MILLIS,
                        true
                ));
            }
        }

        refreshMonthGrid();
        refreshEventList();
        Toast.makeText(this, AppText.pick(this,
                insertedCount + " 件の祝日を入力しました",
                "Added " + insertedCount + " holiday(s)"), Toast.LENGTH_LONG).show();
    }

    private boolean containsHolidayEvent(
            List<CalendarRepository.CalendarEvent> events,
            long startMillis,
            String title
    ) {
        for (CalendarRepository.CalendarEvent event : events) {
            if (event.allDay
                    && event.startMillis == startMillis
                    && TextUtils.equals(event.title, title)) {
                return true;
            }
        }
        return false;
    }

    private void showEventDialog(CalendarRepository.CalendarEvent existingEvent) {
        boolean editing = existingEvent != null;
        if (editing) {
            if (!ensureEventCalendarWritable(existingEvent)) {
                return;
            }
        } else if (!ensureAnyWritableDisplayCalendar()) {
            return;
        }

        List<CalendarRepository.CalendarInfo> writableDisplayCalendars = editing
                ? new ArrayList<>()
                : getWritableDisplayCalendars();
        final long[] targetCalendarId = {
                editing ? existingEvent.calendarId : writableDisplayCalendars.get(0).id
        };

        Calendar startCalendar = Calendar.getInstance();
        Calendar endCalendar = Calendar.getInstance();

        if (editing) {
            startCalendar.setTimeInMillis(existingEvent.startMillis);
            if (existingEvent.allDay) {
                endCalendar.setTimeInMillis(Math.max(existingEvent.startMillis, existingEvent.endMillis - DAY_IN_MILLIS));
            } else {
                endCalendar.setTimeInMillis(existingEvent.endMillis);
            }
        } else {
            startCalendar.setTimeInMillis(defaultStartTime(selectedDayMillis));
            endCalendar.setTimeInMillis(startCalendar.getTimeInMillis());
        }

        int padding = dp(16);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding, padding, 0);

        EditText titleInput = new EditText(this);
        titleInput.setHint(AppText.pick(this, "予定タイトル", "Event title"));
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (editing) {
            titleInput.setText(existingEvent.title);
        }

        EditText notesInput = new EditText(this);
        notesInput.setHint(AppText.pick(this, "メモ", "Notes"));
        notesInput.setMinLines(3);
        notesInput.setGravity(Gravity.TOP | Gravity.START);
        notesInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (editing) {
            notesInput.setText(existingEvent.description);
        }

        CheckBox allDayCheck = new CheckBox(this);
        allDayCheck.setText(AppText.pick(this, "終日予定", "All-day event"));
        allDayCheck.setChecked(editing && existingEvent.allDay);

        Button targetCalendarButton = null;
        if (!editing && writableDisplayCalendars.size() > 1) {
            targetCalendarButton = buildActionButton("");
            Button finalTargetCalendarButton = targetCalendarButton;
            Runnable bindTargetCalendarLabel = () -> {
                CalendarRepository.CalendarInfo info = findCalendarInfo(targetCalendarId[0]);
                String label = info == null
                        ? AppText.pick(this, "登録先を選択", "Select calendar")
                        : buildCalendarPickerLabel(info);
                finalTargetCalendarButton.setText(AppText.pick(this, "登録先: ", "Calendar: ") + label);
            };
            targetCalendarButton.setOnClickListener(v ->
                    showEventTargetCalendarPicker(writableDisplayCalendars, targetCalendarId, bindTargetCalendarLabel));
            bindTargetCalendarLabel.run();
        }

        Button startDateButton = buildActionButton("");
        Button startTimeButton = buildActionButton("");
        Button endDateButton = buildActionButton("");

        Runnable bindDateTimeLabels = () -> {
            startDateButton.setText(AppText.pick(this, "開始日: ", "Start date: ") + formatDate(startCalendar.getTimeInMillis()));
            startTimeButton.setText(AppText.pick(this, "開始時刻: ", "Start time: ") + formatTime(startCalendar.getTimeInMillis()));
            endDateButton.setText(AppText.pick(this, "終了日: ", "End date: ") + formatDate(endCalendar.getTimeInMillis()));
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
        if (targetCalendarButton != null) {
            addFormField(form, targetCalendarButton);
        }
        addFormField(form, startDateButton);
        addFormField(form, startTimeButton);
        addFormField(form, endDateButton);

        ScrollView formScrollView = new ScrollView(this);
        formScrollView.setFillViewport(false);
        formScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        DisplayMetrics dialogMetrics = getResources().getDisplayMetrics();
        int maxDialogFormHeight = Math.max(dp(280), (int) (dialogMetrics.heightPixels * 0.58f));
        formScrollView.addView(form, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        formScrollView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxDialogFormHeight
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing
                        ? AppText.pick(this, "予定を編集", "Edit event")
                        : AppText.pick(this, "予定を追加", "Add event"))
                .setView(formScrollView)
                .setPositiveButton(editing
                        ? AppText.pick(this, "更新", "Update")
                        : AppText.pick(this, "保存", "Save"), null)
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .create();

        dialog.setOnShowListener(ignored -> {
            titleInput.requestFocus();
            titleInput.setSelection(titleInput.getText().length());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                );
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
                    titleInput.setError(AppText.pick(this, "タイトルを入力してください", "Enter a title"));
                    return;
                }

                long startMillis = allDay ? startOfDay(startCalendar.getTimeInMillis()) : startCalendar.getTimeInMillis();
                long endMillis = allDay
                        ? startOfDay(endCalendar.getTimeInMillis()) + DAY_IN_MILLIS
                        : buildDateTimeWithTimeOfDay(endCalendar, startCalendar);

                if ((allDay && endMillis <= startMillis) || (!allDay && endMillis < startMillis)) {
                    Toast.makeText(this, AppText.pick(this,
                            "終了日は開始日以降にしてください",
                            "End date must be on or after the start date"), Toast.LENGTH_LONG).show();
                    return;
                }

                boolean success;
                if (editing) {
                    success = CalendarRepository.updateEvent(
                            this,
                            existingEvent.eventId,
                            existingEvent.calendarId,
                            title,
                            description,
                            startMillis,
                            endMillis,
                            allDay
                    );
                } else {
                    long newEventId = CalendarRepository.insertEvent(
                            this,
                            targetCalendarId[0],
                            title,
                            description,
                            startMillis,
                            endMillis,
                            allDay
                    );
                    success = newEventId >= 0L;
                    if (success) {
                        selectedEventId = -1L;
                    }
                }

                if (!success) {
                    Toast.makeText(this, AppText.pick(this,
                            "予定の保存に失敗しました",
                            "Failed to save event"), Toast.LENGTH_LONG).show();
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
        titleInput.setHint(AppText.pick(this, "TODOタイトル", "TODO title"));
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (editing) {
            titleInput.setText(existingTodo.title);
        }

        addFormField(form, titleInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing
                        ? AppText.pick(this, "TODO を編集", "Edit TODO")
                        : AppText.pick(this, "TODO を追加", "Add TODO"))
                .setView(form)
                .setPositiveButton(editing
                        ? AppText.pick(this, "更新", "Update")
                        : AppText.pick(this, "追加", "Add"), null)
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .create();

        dialog.setOnShowListener(ignored -> {
            titleInput.requestFocus();
            titleInput.setSelection(titleInput.getText().length());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                );
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
                    titleInput.setError(AppText.pick(this, "タイトルを入力してください", "Enter a title"));
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
                    Toast.makeText(this, AppText.pick(this,
                            "TODO の保存に失敗しました",
                            "Failed to save TODO"), Toast.LENGTH_LONG).show();
                    return;
                }

                refreshEventList();
                dialog.dismiss();
                Toast.makeText(this, editing
                        ? AppText.pick(this, "TODO を更新しました", "TODO updated")
                        : AppText.pick(this, "TODO を保存しました", "TODO saved"), Toast.LENGTH_SHORT).show();
            });
        });

        dialog.show();
    }

    private void showTodoActionsDialog(LocalTodoRepository.LocalTodo todo) {
        String[] actions = {
                AppText.pick(this, "編集", "Edit"),
                AppText.pick(this, "上へ移動", "Move up"),
                AppText.pick(this, "下へ移動", "Move down"),
                AppText.pick(this, "削除", "Delete")
        };
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
                        Toast.makeText(this, AppText.pick(this,
                                "TODO の順番を変更しました",
                                "TODO order changed"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, AppText.pick(this,
                                "これ以上は移動できません",
                                "Cannot move any farther"), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void confirmTodoDelete(LocalTodoRepository.LocalTodo todo) {
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "TODO を削除", "Delete TODO"))
                .setMessage(AppText.deleteMessage(this, todo.title))
                .setPositiveButton(AppText.pick(this, "削除", "Delete"), (dialog, which) -> {
                    boolean deleted = LocalTodoRepository.deleteTodo(this, todo.id);
                    if (deleted) {
                        refreshEventList();
                        Toast.makeText(this, AppText.pick(this,
                                "TODO を削除しました",
                                "TODO deleted"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, AppText.pick(this,
                                "TODO の削除に失敗しました",
                                "Failed to delete TODO"), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void editSelectedEvent() {
        if (!ensureCalendarReadyForEventOperation()) {
            return;
        }
        CalendarRepository.CalendarEvent event = getSelectedEvent();
        if (event == null) {
            Toast.makeText(this, AppText.pick(this,
                    "編集する予定を選んでください",
                    "Select an event to edit"), Toast.LENGTH_SHORT).show();
            return;
        }
        showEventDialog(event);
    }

    private void showEventActionsDialog(CalendarRepository.CalendarEvent event) {
        if (!ensureCalendarReadyForEventOperation()) {
            return;
        }
        String[] actions = {
                AppText.pick(this, "編集", "Edit"),
                AppText.pick(this, "削除", "Delete")
        };
        new AlertDialog.Builder(this)
                .setTitle(event.title)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showEventDialog(event);
                        return;
                    }

                    if (which == 1) {
                        showDeleteEventDialog(event);
                    }
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void showDayEventActionPicker(long dayStartMillis) {
        if (!ensureCalendarReadyForEventOperation()) {
            return;
        }

        selectedDayMillis = dayStartMillis;
        selectedEventId = -1L;
        visibleMonth.setTimeInMillis(dayStartMillis);
        resetToMonthStart(visibleMonth);
        refreshMonthGrid();
        refreshEventList();

        String[] eventLabels = new String[eventsForSelectedDay.size() + 1];
        eventLabels[0] = AppText.pick(this, "予定を追加", "Add event");
        for (int i = 0; i < eventsForSelectedDay.size(); i++) {
            eventLabels[i + 1] = buildDeleteEventLabel(eventsForSelectedDay.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "操作を選択", "Select action"))
                .setItems(eventLabels, (dialog, which) -> {
                    if (which == 0) {
                        showEventDialog(null);
                        return;
                    }

                    CalendarRepository.CalendarEvent event = eventsForSelectedDay.get(which - 1);
                    selectedEventId = event.id;
                    scheduleListAdapter.setSelectedEventId(selectedEventId);
                    scheduleListAdapter.notifyDataSetChanged();
                    showEventActionsDialog(event);
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void deleteSelectedEvent() {
        if (!ensureCalendarReadyForEventOperation()) {
            return;
        }
        if (eventsForSelectedDay.isEmpty()) {
            Toast.makeText(this, AppText.pick(this,
                    "削除できる予定がありません",
                    "No events to delete"), Toast.LENGTH_SHORT).show();
            return;
        }

        String[] eventLabels = new String[eventsForSelectedDay.size()];
        for (int i = 0; i < eventsForSelectedDay.size(); i++) {
            eventLabels[i] = buildDeleteEventLabel(eventsForSelectedDay.get(i));
        }

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "削除する予定を選択", "Select event to delete"))
                .setItems(eventLabels, (dialog, which) -> {
                    CalendarRepository.CalendarEvent event = eventsForSelectedDay.get(which);
                    selectedEventId = event.id;
                    scheduleListAdapter.setSelectedEventId(selectedEventId);
                    scheduleListAdapter.notifyDataSetChanged();
                    showDeleteEventDialog(event);
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private String buildDeleteEventLabel(CalendarRepository.CalendarEvent event) {
        String timeText;
        if (event.allDay) {
            timeText = AppText.pick(this, "終日", "All day");
        } else {
            timeText = formatTime(event.startMillis) + " - " + formatTime(event.endMillis);
        }
        return timeText + "  " + event.title;
    }

    private void showDeleteEventDialog(CalendarRepository.CalendarEvent event) {
        if (!ensureEventCalendarWritable(event)) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "予定を削除", "Delete event"))
                .setMessage(AppText.deleteMessage(this, event.title))
                .setPositiveButton(AppText.pick(this, "削除", "Delete"), (dialog, which) -> {
                    boolean deleted;
                    try {
                        deleted = CalendarRepository.deleteEvent(this, event, findCalendarInfo(event.calendarId));
                    } catch (RuntimeException e) {
                        Log.e(TAG, "delete dialog failed for eventId=" + event.eventId, e);
                        deleted = false;
                    }
                    if (deleted) {
                        selectedEventId = -1L;
                        refreshMonthGrid();
                        refreshEventList();
                        Toast.makeText(this, AppText.pick(this,
                                "予定を削除しました",
                                "Event deleted"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, AppText.pick(this,
                                "予定の削除に失敗しました",
                                "Failed to delete event"), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private boolean ensureCalendarReadyForEventOperation() {
        if (!hasCalendarPermissions()) {
            Toast.makeText(this, AppText.pick(this,
                    "カレンダーを同期するには権限を許可してください",
                    "Allow permission to sync the calendar"), Toast.LENGTH_LONG).show();
            requestCalendarPermissions();
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
                    "先にカレンダーを同期してください",
                    "Sync a calendar first"), Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private boolean ensureSelectedCalendarWritable() {
        if (!ensureCalendarReadyForEventOperation()) {
            return false;
        }
        CalendarRepository.CalendarInfo info = findCalendarInfo(selectedCalendarId);
        if (info != null && info.canWrite()) {
            return true;
        }
        Toast.makeText(this, AppText.pick(this,
                "このカレンダーは閲覧のみです。予定を追加・変更できません。",
                "This calendar is read only. Events cannot be added or changed."), Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean ensureAnyWritableDisplayCalendar() {
        if (!ensureCalendarReadyForEventOperation()) {
            return false;
        }
        if (!getWritableDisplayCalendars().isEmpty()) {
            return true;
        }
        Toast.makeText(this, AppText.pick(this,
                "表示中のカレンダーに書き込み可能なものがありません。",
                "No visible calendar can be written to."), Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean ensureEventCalendarWritable(CalendarRepository.CalendarEvent event) {
        if (!ensureCalendarReadyForEventOperation()) {
            return false;
        }
        CalendarRepository.CalendarInfo info = findCalendarInfo(event.calendarId);
        if (info != null && info.canWrite()) {
            return true;
        }
        Toast.makeText(this, AppText.pick(this,
                "この予定のカレンダーは閲覧のみです。変更できません。",
                "This event's calendar is read only and cannot be changed."), Toast.LENGTH_LONG).show();
        return false;
    }

    private List<CalendarRepository.CalendarInfo> getWritableDisplayCalendars() {
        List<CalendarRepository.CalendarInfo> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long calendarId : getDisplayCalendarIds()) {
            CalendarRepository.CalendarInfo info = findCalendarInfo(calendarId);
            if (info != null && info.canWrite() && seen.add(info.id)) {
                result.add(info);
            }
        }
        return result;
    }

    private void showEventTargetCalendarPicker(
            List<CalendarRepository.CalendarInfo> candidates,
            long[] targetCalendarId,
            Runnable onSelected
    ) {
        String[] labels = new String[candidates.size()];
        int checkedItem = 0;
        for (int i = 0; i < candidates.size(); i++) {
            CalendarRepository.CalendarInfo info = candidates.get(i);
            labels[i] = buildCalendarPickerLabel(info);
            if (info.id == targetCalendarId[0]) {
                checkedItem = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "登録先カレンダーを選択", "Select calendar"))
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    targetCalendarId[0] = candidates.get(which).id;
                    onSelected.run();
                    dialog.dismiss();
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private CalendarRepository.CalendarInfo findCalendarInfo(long calendarId) {
        for (CalendarRepository.CalendarInfo info : writableCalendars) {
            if (info.id == calendarId) {
                return info;
            }
        }
        return null;
    }

    private boolean handleMonthGridTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                monthTouchDownX = event.getX();
                monthTouchDownY = event.getY();
                monthLongPressTriggered = false;
                monthLongPressPosition = monthGridView.pointToPosition((int) event.getX(), (int) event.getY());
                startPendingMonthLongPress(view);
                return false;
            case MotionEvent.ACTION_MOVE:
                float moveDeltaX = event.getX() - monthTouchDownX;
                float moveDeltaY = event.getY() - monthTouchDownY;
                int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
                if (Math.abs(moveDeltaX) > touchSlop || Math.abs(moveDeltaY) > touchSlop) {
                    cancelPendingMonthLongPress();
                }
                return false;
            case MotionEvent.ACTION_UP:
                boolean consumed = monthLongPressTriggered;
                cancelPendingMonthLongPress();
                return consumed;
            case MotionEvent.ACTION_CANCEL:
                cancelPendingMonthLongPress();
                return false;
            default:
                return false;
        }
    }

    private void startPendingMonthLongPress(View touchedView) {
        cancelPendingMonthLongPress();
        if (monthLongPressPosition == AdapterView.INVALID_POSITION
                || monthLongPressPosition < 0
                || monthLongPressPosition >= monthDayCells.size()) {
            return;
        }

        monthLongPressRunnable = () -> {
            if (monthLongPressPosition == AdapterView.INVALID_POSITION
                    || monthLongPressPosition < 0
                    || monthLongPressPosition >= monthDayCells.size()) {
                return;
            }
            monthLongPressTriggered = true;
            touchedView.performLongClick();
            MonthDayCell cell = monthDayCells.get(monthLongPressPosition);
            showDayEventActionPicker(cell.dayStartMillis);
        };
        monthLongPressHandler.postDelayed(monthLongPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelPendingMonthLongPress() {
        if (monthLongPressRunnable != null) {
            monthLongPressHandler.removeCallbacks(monthLongPressRunnable);
            monthLongPressRunnable = null;
        }
    }

    private void shiftMonth(int delta) {
        visibleMonth.add(Calendar.MONTH, delta);
        resetToMonthStart(visibleMonth);
        refreshMonthGrid();
        refreshEventList();
    }

    private void jumpToToday() {
        selectedDayMillis = startOfDay(System.currentTimeMillis());
        selectedEventId = -1L;
        resetScheduleListPosition = true;
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
        button.setTextColor(PRIMARY_TEXT_COLOR);
        GradientDrawable background = new GradientDrawable();
        background.setColor(BUTTON_BACKGROUND_COLOR);
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), BUTTON_BORDER_COLOR);
        button.setBackground(background);
        return button;
    }

    private Button buildCompactActionButton(String text) {
        Button button = buildActionButton(text);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        return button;
    }

    private Button buildTightActionButton(String text) {
        Button button = buildActionButton(text);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(7), dp(6), dp(7), dp(6));
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

    private LinearLayout.LayoutParams createTightWeightedButtonLayoutParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = 1f;
        params.rightMargin = dp(1);
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
                .setTitle(AppText.pick(this, "TODO を読み込み", "Load TODO"))
                .setMessage(AppText.pick(this,
                        "ファイルの内容で現在の TODO を置き換えます。",
                        "Replace current TODO items with the file contents."))
                .setPositiveButton(AppText.pick(this, "読込", "Load"), (dialog, which) -> launchTodoImport())
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void launchTodoImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_TODOS);
    }

    private void showWallpaperMenu() {
        String[] items = {
                AppText.pick(this, "壁紙選択", "Choose wallpaper"),
                AppText.pick(this, "壁紙なし", "No wallpaper")
        };
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "壁紙", "Wallpaper"))
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        launchWallpaperPicker();
                    } else {
                        removeSelectedWallpaper();
                    }
                })
                .show();
    }

    private void launchWallpaperPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    private void saveWallpaperUri(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_WALLPAPER_URI, uri.toString())
                .apply();
        applyWallpaperUri(uri);
        Toast.makeText(this, AppText.pick(this,
                "壁紙を設定しました",
                "Wallpaper set"), Toast.LENGTH_SHORT).show();
    }

    private void removeSelectedWallpaper() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_WALLPAPER_URI)
                .apply();
        if (wallpaperView != null) {
            wallpaperView.setImageDrawable(null);
            wallpaperView.setVisibility(View.GONE);
        }
        updateCalendarWallpaperTheme(false);
        Toast.makeText(this, AppText.pick(this,
                "壁紙なしにしました",
                "Wallpaper removed"), Toast.LENGTH_SHORT).show();
    }

    private void applyPersistedWallpaper() {
        String rawUri = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_WALLPAPER_URI, "");
        if (TextUtils.isEmpty(rawUri)) {
            if (wallpaperView != null) {
                wallpaperView.setVisibility(View.GONE);
            }
            updateCalendarWallpaperTheme(false);
            return;
        }
        applyWallpaperUri(Uri.parse(rawUri));
    }

    private void applyWallpaperUri(Uri uri) {
        if (wallpaperView == null) {
            return;
        }
        try {
            wallpaperView.setImageURI(uri);
            wallpaperView.setAlpha(WALLPAPER_ALPHA);
            wallpaperView.setVisibility(View.VISIBLE);
            updateCalendarWallpaperTheme(true);
        } catch (RuntimeException e) {
            wallpaperView.setVisibility(View.GONE);
            updateCalendarWallpaperTheme(false);
            Toast.makeText(this, AppText.pick(this,
                    "壁紙画像を読み込めません",
                    "Could not load wallpaper"), Toast.LENGTH_LONG).show();
        }
    }

    private boolean hasPersistedWallpaper() {
        return !TextUtils.isEmpty(getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_WALLPAPER_URI, ""));
    }

    private void updateCalendarWallpaperTheme(boolean wallpaperEnabled) {
        if (monthCalendarAdapter != null) {
            monthCalendarAdapter.setWallpaperEnabled(wallpaperEnabled);
        }
    }

    private void exportTodosToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("output stream is null");
            }

            String json = LocalTodoRepository.exportTodosAsJson(this);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Toast.makeText(this, AppText.pick(this, "TODO を保存しました", "TODO saved"), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, AppText.pick(this,
                    "TODO の保存に失敗しました",
                    "Failed to save TODO"), Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, AppText.importedTodos(this, importedCount), Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException e) {
            Toast.makeText(this, AppText.pick(this,
                    "TODO の読み込みに失敗しました",
                    "Failed to load TODO"), Toast.LENGTH_LONG).show();
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

    private long loadPersistedCalendarId() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getLong(KEY_SELECTED_CALENDAR_ID, -1L);
    }

    private void persistCalendarId(long calendarId) {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putLong(KEY_SELECTED_CALENDAR_ID, calendarId).apply();
    }

    private Set<Long> loadPersistedExtraCalendarIds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_EXTRA_CALENDAR_IDS, new HashSet<>());
        Set<Long> result = new LinkedHashSet<>();
        for (String s : stored) {
            try { result.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private void persistExtraCalendarIds() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> toStore = new LinkedHashSet<>();
        for (long id : extraCalendarIds) toStore.add(String.valueOf(id));
        prefs.edit().putStringSet(KEY_EXTRA_CALENDAR_IDS, toStore).apply();
    }

    private List<Long> getDisplayCalendarIds() {
        List<Long> ids = new ArrayList<>();
        if (selectedCalendarId >= 0L) ids.add(selectedCalendarId);
        for (long id : extraCalendarIds) {
            if (id != selectedCalendarId) ids.add(id);
        }
        return ids;
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

    private int dp(int value) {
        if (value == 0) {
            return 0;
        }
        int pixels = Math.round(value * getResources().getDisplayMetrics().density);
        return value > 0 ? Math.max(1, pixels) : Math.min(-1, pixels);
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

    private long buildLocalDateMillis(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
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
        SimpleDateFormat format = new SimpleDateFormat(AppText.selectedDatePattern(this), AppText.displayLocale(this));
        return AppText.selectedDateTitle(this, format.format(new Date(timeInMillis)));
    }

    private String formatVisibleMonth(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat(AppText.visibleMonthPattern(this), AppText.displayLocale(this));
        return format.format(new Date(timeInMillis));
    }

    private String formatDate(long timeInMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault());
        return format.format(new Date(timeInMillis));
    }

    private String formatTime(long timeInMillis) {
        if (!AppText.isJapan(this)) {
            SimpleDateFormat format = new SimpleDateFormat("h:mm a", Locale.ENGLISH);
            return format.format(new Date(timeInMillis));
        }
        java.text.DateFormat format = DateFormat.getTimeFormat(this);
        return format.format(new Date(timeInMillis));
    }
}
