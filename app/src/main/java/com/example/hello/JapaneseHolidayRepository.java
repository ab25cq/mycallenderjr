package com.example.myhelloworld;

import java.util.ArrayList;
import java.util.List;

final class JapaneseHolidayRepository {
    private JapaneseHolidayRepository() {
    }

    static List<Holiday> getHolidaysForYear(int year) {
        List<Holiday> holidays = new ArrayList<>();
        if (year == 2026) {
            add2026(holidays);
        } else if (year == 2027) {
            add2027(holidays);
        }
        return holidays;
    }

    static boolean isHoliday(int year, int month, int day) {
        List<Holiday> holidays = getHolidaysForYear(year);
        for (Holiday holiday : holidays) {
            if (holiday.month == month && holiday.day == day) {
                return true;
            }
        }
        return false;
    }

    private static void add2026(List<Holiday> holidays) {
        holidays.add(new Holiday(1, 1, "元日"));
        holidays.add(new Holiday(1, 12, "成人の日"));
        holidays.add(new Holiday(2, 11, "建国記念の日"));
        holidays.add(new Holiday(2, 23, "天皇誕生日"));
        holidays.add(new Holiday(3, 20, "春分の日"));
        holidays.add(new Holiday(4, 29, "昭和の日"));
        holidays.add(new Holiday(5, 3, "憲法記念日"));
        holidays.add(new Holiday(5, 4, "みどりの日"));
        holidays.add(new Holiday(5, 5, "こどもの日"));
        holidays.add(new Holiday(5, 6, "休日"));
        holidays.add(new Holiday(7, 20, "海の日"));
        holidays.add(new Holiday(8, 11, "山の日"));
        holidays.add(new Holiday(9, 21, "敬老の日"));
        holidays.add(new Holiday(9, 22, "休日"));
        holidays.add(new Holiday(9, 23, "秋分の日"));
        holidays.add(new Holiday(10, 12, "スポーツの日"));
        holidays.add(new Holiday(11, 3, "文化の日"));
        holidays.add(new Holiday(11, 23, "勤労感謝の日"));
    }

    private static void add2027(List<Holiday> holidays) {
        holidays.add(new Holiday(1, 1, "元日"));
        holidays.add(new Holiday(1, 11, "成人の日"));
        holidays.add(new Holiday(2, 11, "建国記念の日"));
        holidays.add(new Holiday(2, 23, "天皇誕生日"));
        holidays.add(new Holiday(3, 21, "春分の日"));
        holidays.add(new Holiday(3, 22, "休日"));
        holidays.add(new Holiday(4, 29, "昭和の日"));
        holidays.add(new Holiday(5, 3, "憲法記念日"));
        holidays.add(new Holiday(5, 4, "みどりの日"));
        holidays.add(new Holiday(5, 5, "こどもの日"));
        holidays.add(new Holiday(7, 19, "海の日"));
        holidays.add(new Holiday(8, 11, "山の日"));
        holidays.add(new Holiday(9, 20, "敬老の日"));
        holidays.add(new Holiday(9, 23, "秋分の日"));
        holidays.add(new Holiday(10, 11, "スポーツの日"));
        holidays.add(new Holiday(11, 3, "文化の日"));
        holidays.add(new Holiday(11, 23, "勤労感謝の日"));
    }

    static final class Holiday {
        final int month;
        final int day;
        final String title;

        Holiday(int month, int day, String title) {
            this.month = month;
            this.day = day;
            this.title = title;
        }
    }
}
