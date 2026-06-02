package com.example.myhelloworld;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LocalDiaryRepository {
    private static final String PREFS_NAME = "local_diary";
    private static final String KEY_ENTRIES = "entries";
    private static final String IMAGE_DIR = "diary_images";

    private LocalDiaryRepository() {
    }

    public static List<DiaryEntry> getEntries(Context context) {
        List<DiaryEntry> entries = new ArrayList<>();
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ENTRIES, "[]");
        try {
            JSONArray jsonArray = new JSONArray(raw);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject item = jsonArray.getJSONObject(i);
                entries.add(new DiaryEntry(
                        item.optString("id"),
                        item.optLong("created_at"),
                        item.optString("text"),
                        item.optString("image_path")
                ));
            }
        } catch (JSONException ignored) {
        }
        return entries;
    }

    public static DiaryEntry addEntry(Context context, String text, String imagePath) {
        List<DiaryEntry> entries = getEntries(context);
        DiaryEntry entry = new DiaryEntry(
                UUID.randomUUID().toString(),
                System.currentTimeMillis(),
                text == null ? "" : text,
                imagePath == null ? "" : imagePath
        );
        entries.add(0, entry);
        saveEntries(context, entries);
        return entry;
    }

    public static boolean deleteEntry(Context context, String entryId) {
        List<DiaryEntry> entries = getEntries(context);
        boolean removed = false;
        String imagePath = "";
        for (int i = entries.size() - 1; i >= 0; i--) {
            DiaryEntry entry = entries.get(i);
            if (TextUtils.equals(entry.id, entryId)) {
                imagePath = entry.imagePath;
                entries.remove(i);
                removed = true;
            }
        }
        if (removed) {
            saveEntries(context, entries);
            deleteImageFile(imagePath);
        }
        return removed;
    }

    public static String saveCameraBitmap(Context context, Bitmap bitmap) throws IOException {
        File dir = new File(context.getFilesDir(), IMAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("image directory cannot be created");
        }
        File file = new File(dir, UUID.randomUUID().toString() + ".jpg");
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream);
        }
        return file.getAbsolutePath();
    }

    public static String saveImageFromUri(Context context, Uri uri) throws IOException {
        File dir = new File(context.getFilesDir(), IMAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("image directory cannot be created");
        }
        File file = new File(dir, UUID.randomUUID().toString() + ".jpg");
        byte[] buffer = new byte[8192];
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(file)) {
            if (inputStream == null) {
                throw new IOException("image input stream is null");
            }
            int readSize;
            while ((readSize = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readSize);
            }
        }
        return file.getAbsolutePath();
    }

    public static String exportEntriesAsJson(Context context) throws IOException {
        JSONArray jsonArray = new JSONArray();
        for (DiaryEntry entry : getEntries(context)) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", entry.id);
                item.put("created_at", entry.createdAt);
                item.put("text", entry.text);
                item.put("image_name", TextUtils.isEmpty(entry.imagePath) ? "" : new File(entry.imagePath).getName());
                item.put("image_base64", encodeImage(entry.imagePath));
                jsonArray.put(item);
            } catch (JSONException ignored) {
            }
        }
        return jsonArray.toString();
    }

    public static int importEntriesFromJson(Context context, String rawJson) throws JSONException, IOException {
        JSONArray jsonArray = new JSONArray(rawJson);
        List<DiaryEntry> oldEntries = getEntries(context);
        List<DiaryEntry> importedEntries = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);
            String imagePath = saveImportedImage(
                    context,
                    item.optString("image_name"),
                    item.optString("image_base64")
            );
            long createdAt = item.optLong("created_at", System.currentTimeMillis());
            if (createdAt <= 0) {
                createdAt = System.currentTimeMillis();
            }
            importedEntries.add(new DiaryEntry(
                    item.optString("id", UUID.randomUUID().toString()),
                    createdAt,
                    item.optString("text"),
                    imagePath
            ));
        }
        saveEntries(context, importedEntries);
        deleteEntryImages(oldEntries);
        return importedEntries.size();
    }

    private static String encodeImage(String imagePath) throws IOException {
        if (TextUtils.isEmpty(imagePath)) {
            return "";
        }
        File file = new File(imagePath);
        if (!file.exists()) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int readSize;
            while ((readSize = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, readSize);
            }
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private static String saveImportedImage(Context context, String imageName, String imageBase64) throws IOException {
        if (TextUtils.isEmpty(imageBase64)) {
            return "";
        }

        File dir = new File(context.getFilesDir(), IMAGE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("image directory cannot be created");
        }

        String extension = extensionFromName(imageName);
        File file = new File(dir, UUID.randomUUID().toString() + extension);
        byte[] imageBytes = Base64.decode(imageBase64, Base64.DEFAULT);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(imageBytes);
        }
        return file.getAbsolutePath();
    }

    private static String extensionFromName(String imageName) {
        if (TextUtils.isEmpty(imageName)) {
            return ".jpg";
        }
        int dotIndex = imageName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == imageName.length() - 1) {
            return ".jpg";
        }
        String extension = imageName.substring(dotIndex).toLowerCase();
        if (!extension.matches("\\.[a-z0-9]{1,8}")) {
            return ".jpg";
        }
        return extension;
    }

    private static void saveEntries(Context context, List<DiaryEntry> entries) {
        JSONArray jsonArray = new JSONArray();
        for (DiaryEntry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", entry.id);
                item.put("created_at", entry.createdAt);
                item.put("text", entry.text);
                item.put("image_path", entry.imagePath);
                jsonArray.put(item);
            } catch (JSONException ignored) {
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, jsonArray.toString())
                .apply();
    }

    private static void deleteEntryImages(List<DiaryEntry> entries) {
        for (DiaryEntry entry : entries) {
            deleteImageFile(entry.imagePath);
        }
    }

    private static void deleteImageFile(String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            return;
        }
        File file = new File(imagePath);
        if (file.exists()) {
            file.delete();
        }
    }

    public static final class DiaryEntry {
        public final String id;
        public final long createdAt;
        public final String text;
        public final String imagePath;

        DiaryEntry(String id, long createdAt, String text, String imagePath) {
            this.id = id;
            this.createdAt = createdAt;
            this.text = text;
            this.imagePath = imagePath;
        }
    }
}
