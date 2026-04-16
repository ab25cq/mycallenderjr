package com.example.myhelloworld;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LocalTodoRepository {
    private static final String PREFS_NAME = "local_todos";
    private static final String KEY_GLOBAL_TODOS = "global_todos";

    private LocalTodoRepository() {
    }

    public static List<LocalTodo> getTodos(Context context) {
        List<LocalTodo> todos = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_GLOBAL_TODOS, "[]");

        try {
            JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                todos.add(new LocalTodo(
                        item.optString("id"),
                        item.optString("title", "(無題)")
                ));
            }
        } catch (JSONException ignored) {
        }

        return todos;
    }

    public static LocalTodo addTodo(Context context, String title) {
        List<LocalTodo> todos = getTodos(context);
        LocalTodo todo = new LocalTodo(UUID.randomUUID().toString(), title);
        todos.add(0, todo);
        saveTodos(context, todos);
        return todo;
    }

    public static boolean deleteTodo(Context context, String todoId) {
        List<LocalTodo> todos = getTodos(context);
        boolean removed = false;

        for (int i = todos.size() - 1; i >= 0; i--) {
            if (TextUtils.equals(todos.get(i).id, todoId)) {
                todos.remove(i);
                removed = true;
            }
        }

        if (removed) {
            saveTodos(context, todos);
        }
        return removed;
    }

    public static boolean updateTodo(Context context, String todoId, String title) {
        List<LocalTodo> todos = getTodos(context);
        boolean updated = false;

        for (int i = 0; i < todos.size(); i++) {
            LocalTodo todo = todos.get(i);
            if (TextUtils.equals(todo.id, todoId)) {
                todos.set(i, new LocalTodo(todo.id, title));
                updated = true;
                break;
            }
        }

        if (updated) {
            saveTodos(context, todos);
        }
        return updated;
    }

    public static boolean moveTodo(Context context, String todoId, int offset) {
        if (offset == 0) {
            return false;
        }

        List<LocalTodo> todos = getTodos(context);
        for (int i = 0; i < todos.size(); i++) {
            if (TextUtils.equals(todos.get(i).id, todoId)) {
                int targetIndex = i + offset;
                if (targetIndex < 0 || targetIndex >= todos.size()) {
                    return false;
                }

                LocalTodo moving = todos.remove(i);
                todos.add(targetIndex, moving);
                saveTodos(context, todos);
                return true;
            }
        }

        return false;
    }

    public static String exportTodosAsJson(Context context) {
        List<LocalTodo> todos = getTodos(context);
        JSONArray jsonArray = new JSONArray();
        for (LocalTodo todo : todos) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", todo.id);
                item.put("title", todo.title);
                jsonArray.put(item);
            } catch (JSONException ignored) {
            }
        }
        return jsonArray.toString();
    }

    public static int importTodosFromJson(Context context, String rawJson) throws JSONException {
        JSONArray jsonArray = new JSONArray(rawJson);
        List<LocalTodo> todos = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);
            String id = item.optString("id");
            if (TextUtils.isEmpty(id)) {
                id = UUID.randomUUID().toString();
            }
            String title = item.optString("title", "(無題)");
            todos.add(new LocalTodo(id, title));
        }

        saveTodos(context, todos);
        return todos.size();
    }

    private static void saveTodos(Context context, List<LocalTodo> todos) {
        JSONArray jsonArray = new JSONArray();
        for (LocalTodo todo : todos) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", todo.id);
                item.put("title", todo.title);
                jsonArray.put(item);
            } catch (JSONException ignored) {
            }
        }

        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_GLOBAL_TODOS, jsonArray.toString()).apply();
    }

    public static final class LocalTodo {
        public final String id;
        public final String title;

        LocalTodo(String id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
