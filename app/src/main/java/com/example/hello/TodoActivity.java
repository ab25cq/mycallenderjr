package com.example.myhelloworld;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoActivity extends Activity {
    private static final int REQUEST_EXPORT_TODOS = 4001;
    private static final int REQUEST_IMPORT_TODOS = 4002;
    private static final int KEYBOARD_VISIBILITY_THRESHOLD_DP = 120;

    private final List<ScheduleListAdapter.ScheduleListItem> todoItems = new ArrayList<>();
    private ListView todoListView;
    private ScheduleListAdapter todoListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
        }
        buildLayout();
        refreshTodoList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTodoList();
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
        int padding = dp(10);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView titleView = new TextView(this);
        titleView.setText("TODO");
        titleView.setTextSize(20);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(titleView, matchWrapParams());

        todoListAdapter = new ScheduleListAdapter(this, todoItems);
        todoListView = new ListView(this);
        todoListView.setDividerHeight(dp(2));
        todoListView.setSelector(android.R.color.transparent);
        todoListView.setAdapter(todoListAdapter);
        todoListView.setOnItemClickListener((parent, view, position, id) -> {
            ScheduleListAdapter.ScheduleListItem item =
                    (ScheduleListAdapter.ScheduleListItem) todoListAdapter.getItem(position);
            if (item.isTodo()) {
                showTodoActionsDialog(item.todo);
            }
        });
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
        );
        listParams.weight = 1f;
        root.addView(todoListView, listParams);

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setPadding(0, dp(6), 0, 0);

        Button addButton = buildButton(AppText.pick(this, "追加", "Add"));
        addButton.setOnClickListener(v -> showTodoDialog(null));
        Button exportButton = buildButton(AppText.pick(this, "保存", "Save"));
        exportButton.setOnClickListener(v -> launchTodoExport());
        Button importButton = buildButton(AppText.pick(this, "読込", "Load"));
        importButton.setOnClickListener(v -> confirmTodoImport());
        Button backButton = buildButton(AppText.pick(this, "戻る", "Back"));
        backButton.setOnClickListener(v -> finish());

        actionBar.addView(addButton, weightedParams(true));
        actionBar.addView(exportButton, weightedParams(true));
        actionBar.addView(importButton, weightedParams(true));
        actionBar.addView(backButton, weightedParams(false));
        root.addView(actionBar, matchWrapParams());

        setContentView(root);
        installSystemAwareRootPadding(root);
    }

    private void refreshTodoList() {
        todoItems.clear();
        List<LocalTodoRepository.LocalTodo> todos = LocalTodoRepository.getTodos(this);
        if (todos.isEmpty()) {
            todoItems.add(ScheduleListAdapter.ScheduleListItem.message(AppText.pick(this,
                    "TODO はありません。",
                    "No TODO items.")));
        } else {
            for (LocalTodoRepository.LocalTodo todo : todos) {
                todoItems.add(ScheduleListAdapter.ScheduleListItem.todo(todo));
            }
        }
        todoListAdapter.replaceItems(todoItems);
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
        form.addView(titleInput, matchWrapParams());

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

                refreshTodoList();
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
                        refreshTodoList();
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
                        refreshTodoList();
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
            refreshTodoList();
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
