package com.example.myhelloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DiaryActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 5001;
    private static final int REQUEST_EXPORT_DIARY = 5002;
    private static final int REQUEST_IMPORT_DIARY = 5003;
    private static final int KEYBOARD_VISIBILITY_THRESHOLD_DP = 120;

    private EditText diaryInput;
    private ImageView pendingImageView;
    private LinearLayout timelineLayout;
    private String pendingImagePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        buildLayout();
        refreshTimeline();
    }

    @Override
    protected void onResume() {
        super.onResume();
        showKeyboard();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_PICK_IMAGE && data != null && data.getData() != null) {
            handleSelectedImage(data.getData());
            return;
        }

        if (requestCode == REQUEST_EXPORT_DIARY && data != null && data.getData() != null) {
            exportDiaryToUri(data.getData());
            return;
        }

        if (requestCode == REQUEST_IMPORT_DIARY && data != null && data.getData() != null) {
            importDiaryFromUri(data.getData());
        }
    }

    private void buildLayout() {
        int padding = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText(AppText.pick(this, "日記", "Diary"));
        titleView.setTextSize(20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(titleView, matchWrapParams());

        diaryInput = new EditText(this);
        diaryInput.setHint(AppText.pick(this, "入力してEnterで投稿", "Type and press Enter to post"));
        diaryInput.setMinLines(1);
        diaryInput.setMaxLines(3);
        diaryInput.setGravity(Gravity.TOP | Gravity.START);
        diaryInput.setHorizontallyScrolling(false);
        diaryInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        diaryInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        diaryInput.setOnEditorActionListener((view, actionId, event) -> {
            boolean sendAction = actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE;
            boolean enterUp = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (sendAction || enterUp) {
                postDiaryEntry();
                return true;
            }
            return false;
        });
        diaryInput.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP) {
                postDiaryEntry();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_ENTER;
        });
        root.addView(diaryInput, matchWrapParams());

        pendingImageView = new ImageView(this);
        pendingImageView.setAdjustViewBounds(true);
        pendingImageView.setMaxHeight(dp(160));
        pendingImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pendingImageView.setVisibility(View.GONE);
        LinearLayout.LayoutParams previewParams = matchWrapParams();
        previewParams.topMargin = dp(6);
        root.addView(pendingImageView, previewParams);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, dp(6), 0, dp(6));

        Button postButton = buildButton(AppText.pick(this, "投稿", "Post"));
        postButton.setOnClickListener(v -> postDiaryEntry());
        Button imageButton = buildButton(AppText.pick(this, "画像", "Image"));
        imageButton.setOnClickListener(v -> launchImagePicker());
        Button saveButton = buildButton(AppText.pick(this, "保存", "Save"));
        saveButton.setOnClickListener(v -> launchDiaryExport());
        Button loadButton = buildButton(AppText.pick(this, "読込", "Load"));
        loadButton.setOnClickListener(v -> confirmDiaryImport());
        Button backButton = buildButton(AppText.pick(this, "戻る", "Back"));
        backButton.setOnClickListener(v -> finish());

        actionBar.addView(postButton, weightedParams(true));
        actionBar.addView(imageButton, weightedParams(true));
        actionBar.addView(saveButton, weightedParams(true));
        actionBar.addView(loadButton, weightedParams(true));
        actionBar.addView(backButton, weightedParams(false));
        root.addView(actionBar, matchWrapParams());

        ScrollView timelineScrollView = new ScrollView(this);
        timelineLayout = new LinearLayout(this);
        timelineLayout.setOrientation(LinearLayout.VERTICAL);
        timelineScrollView.addView(timelineLayout, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams timelineParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
        );
        timelineParams.weight = 1f;
        root.addView(timelineScrollView, timelineParams);

        setContentView(root);
        installSystemAwareRootPadding(root);
        diaryInput.post(this::showKeyboard);
    }

    private void postDiaryEntry() {
        String text = diaryInput.getText().toString().trim();
        if (TextUtils.isEmpty(text) && TextUtils.isEmpty(pendingImagePath)) {
            diaryInput.setError(AppText.pick(this, "日記を入力してください", "Enter a diary note"));
            return;
        }

        LocalDiaryRepository.addEntry(this, text, pendingImagePath);
        diaryInput.setText("");
        pendingImagePath = "";
        pendingImageView.setImageDrawable(null);
        pendingImageView.setVisibility(View.GONE);
        refreshTimeline();
        showKeyboard();
        Toast.makeText(this, AppText.pick(this, "日記を保存しました", "Diary saved"), Toast.LENGTH_SHORT).show();
    }

    private void launchImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(Intent.createChooser(intent, AppText.pick(this,
                    "画像を選択",
                    "Choose image")), REQUEST_PICK_IMAGE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, AppText.pick(this,
                    "画像を選択できるアプリが見つかりません",
                    "No app found to choose images"), Toast.LENGTH_LONG).show();
        }
    }

    private void handleSelectedImage(Uri uri) {
        try {
            pendingImagePath = LocalDiaryRepository.saveImageFromUri(this, uri);
            pendingImageView.setImageURI(Uri.fromFile(new File(pendingImagePath)));
            pendingImageView.setVisibility(View.VISIBLE);
            Toast.makeText(this, AppText.pick(this,
                    "画像を追加しました",
                    "Image attached"), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, AppText.pick(this,
                    "画像の保存に失敗しました",
                    "Failed to save image"), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshTimeline() {
        timelineLayout.removeAllViews();
        List<LocalDiaryRepository.DiaryEntry> entries = LocalDiaryRepository.getEntries(this);
        if (entries.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText(AppText.pick(this, "日記はありません。", "No diary entries."));
            emptyView.setTextSize(15);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(0, dp(24), 0, 0);
            timelineLayout.addView(emptyView, matchWrapParams());
            return;
        }

        for (LocalDiaryRepository.DiaryEntry entry : entries) {
            timelineLayout.addView(buildEntryView(entry), matchWrapParams());
        }
    }

    private View buildEntryView(LocalDiaryRepository.DiaryEntry entry) {
        LinearLayout entryLayout = new LinearLayout(this);
        entryLayout.setOrientation(LinearLayout.VERTICAL);
        entryLayout.setPadding(dp(10), dp(8), dp(10), dp(8));
        entryLayout.setBackgroundColor(0xEEFFFFFF);

        TextView timeView = new TextView(this);
        timeView.setText(formatEntryTime(entry.createdAt));
        timeView.setTextSize(12);
        timeView.setTextColor(0xFF555555);
        entryLayout.addView(timeView, matchWrapParams());

        if (!TextUtils.isEmpty(entry.text)) {
            TextView textView = new TextView(this);
            textView.setText(entry.text);
            textView.setTextSize(16);
            textView.setTextColor(0xFF111111);
            textView.setPadding(0, dp(4), 0, 0);
            entryLayout.addView(textView, matchWrapParams());
        }

        if (!TextUtils.isEmpty(entry.imagePath)) {
            File imageFile = new File(entry.imagePath);
            if (imageFile.exists()) {
                ImageView imageView = new ImageView(this);
                imageView.setAdjustViewBounds(true);
                imageView.setMaxHeight(dp(240));
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setImageURI(Uri.fromFile(imageFile));
                LinearLayout.LayoutParams imageParams = matchWrapParams();
                imageParams.topMargin = dp(6);
                entryLayout.addView(imageView, imageParams);
            }
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);
        entryLayout.setLayoutParams(params);
        entryLayout.setOnLongClickListener(v -> {
            confirmEntryDelete(entry);
            return true;
        });
        return entryLayout;
    }

    private String formatEntryTime(long createdAt) {
        String pattern = AppText.pick(this, "yyyy年M月d日 HH:mm", "yyyy-MM-dd HH:mm");
        return new SimpleDateFormat(pattern, AppText.displayLocale(this)).format(new Date(createdAt));
    }

    private void confirmEntryDelete(LocalDiaryRepository.DiaryEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "日記を削除", "Delete diary entry"))
                .setMessage(AppText.pick(this,
                        "この日記を削除しますか。",
                        "Delete this diary entry?"))
                .setPositiveButton(AppText.pick(this, "削除", "Delete"), (dialog, which) -> {
                    boolean deleted = LocalDiaryRepository.deleteEntry(this, entry.id);
                    if (deleted) {
                        refreshTimeline();
                        Toast.makeText(this, AppText.pick(this,
                                "日記を削除しました",
                                "Diary entry deleted"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, AppText.pick(this,
                                "日記の削除に失敗しました",
                                "Failed to delete diary entry"), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void launchDiaryExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, buildDiaryBackupFileName());
        startActivityForResult(intent, REQUEST_EXPORT_DIARY);
    }

    private void confirmDiaryImport() {
        new AlertDialog.Builder(this)
                .setTitle(AppText.pick(this, "日記を読み込み", "Load diary"))
                .setMessage(AppText.pick(this,
                        "ファイルの内容で現在の日記を置き換えます。",
                        "Replace current diary entries with the file contents."))
                .setPositiveButton(AppText.pick(this, "読込", "Load"), (dialog, which) -> launchDiaryImport())
                .setNegativeButton(AppText.pick(this, "キャンセル", "Cancel"), null)
                .show();
    }

    private void launchDiaryImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_DIARY);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, AppText.pick(this,
                    "ファイルを選択できるアプリが見つかりません",
                    "No app found to choose files"), Toast.LENGTH_LONG).show();
        }
    }

    private void exportDiaryToUri(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("output stream is null");
            }

            String json = LocalDiaryRepository.exportEntriesAsJson(this);
            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            Toast.makeText(this, AppText.pick(this, "日記を保存しました", "Diary saved"), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, AppText.pick(this,
                    "日記の保存に失敗しました",
                    "Failed to save diary"), Toast.LENGTH_LONG).show();
        }
    }

    private void importDiaryFromUri(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                throw new IOException("input stream is null");
            }

            String json = readAllText(inputStream);
            int importedCount = LocalDiaryRepository.importEntriesFromJson(this, json);
            pendingImagePath = "";
            pendingImageView.setImageDrawable(null);
            pendingImageView.setVisibility(View.GONE);
            refreshTimeline();
            Toast.makeText(this, AppText.pick(this,
                    importedCount + " 件の日記を読み込みました",
                    "Imported " + importedCount + " diary entries"), Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Toast.makeText(this, AppText.pick(this,
                    "日記の読み込みに失敗しました",
                    "Failed to load diary"), Toast.LENGTH_LONG).show();
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

    private String buildDiaryBackupFileName() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault());
        return "diary_backup_" + format.format(new Date()) + ".json";
    }

    private void showKeyboard() {
        diaryInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(diaryInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private Button buildButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(6), dp(4), dp(6), dp(4));
        return button;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedParams(boolean rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.weight = 1f;
        if (rightMargin) {
            params.rightMargin = dp(8);
        }
        return params;
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
}
