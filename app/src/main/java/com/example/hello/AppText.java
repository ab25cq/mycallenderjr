package com.example.myhelloworld;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

import java.util.Locale;

final class AppText {
    private AppText() {
    }

    static boolean isJapan(Context context) {
        Locale locale;
        Configuration configuration = context.getResources().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale = configuration.getLocales().get(0);
        } else {
            locale = configuration.locale;
        }
        return isJapanLocale(locale);
    }

    static boolean isJapanLocale() {
        return isJapanLocale(Locale.getDefault());
    }

    private static boolean isJapanLocale(Locale locale) {
        return locale != null && "JP".equalsIgnoreCase(locale.getCountry());
    }

    static String pick(Context context, String ja, String en) {
        return isJapan(context) ? ja : en;
    }

    static Locale displayLocale(Context context) {
        return isJapan(context) ? Locale.getDefault() : Locale.ENGLISH;
    }

    static String pick(String ja, String en) {
        return isJapanLocale() ? ja : en;
    }

    static String appName(Context context) {
        return pick(context, "カレンダー", "Calendar");
    }

    static String[] weekLabels(Context context) {
        return isJapan(context)
                ? new String[]{"月", "火", "水", "木", "金", "土", "日"}
                : new String[]{"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
    }

    static String selectedDatePattern(Context context) {
        return pick(context, "yyyy年M月d日 (E)", "EEE, MMM d, yyyy");
    }

    static String visibleMonthPattern(Context context) {
        return pick(context, "yyyy年M月", "MMMM yyyy");
    }

    static String selectedDateTitle(Context context, String formattedDate) {
        return pick(context, formattedDate + " の予定", "Schedule for " + formattedDate);
    }

    static String deleteMessage(Context context, String title) {
        return pick(context, "「" + title + "」を削除しますか。", "Delete \"" + title + "\"?");
    }

    static String importedTodos(Context context, int count) {
        return pick(context, count + " 件の TODO を読み込みました", "Imported " + count + " TODO item(s)");
    }

    static String untitled() {
        return pick("(無題)", "(Untitled)");
    }

    static String unnamedCalendar() {
        return pick("(名称なし)", "(No name)");
    }

    static String noAccount() {
        return pick("(アカウントなし)", "(No account)");
    }
}
