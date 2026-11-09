package com.angelasoft.presidium;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;

/** 주회합·평의회 월례회의 날짜 계산. 한 주는 월~일. */
public final class MeetingCalendar {

    /** 평의회 월례회의 "마지막 주". */
    public static final int LAST_WEEK = 5;

    private MeetingCalendar() {
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** [from] 부터 휴회 주를 건너뛴 다음 주회합 날짜. 요일이 없으면 null. */
    public static LocalDate nextPresidiumMeeting(DayOfWeek day, Collection<LocalDate> recessWeeks, LocalDate from) {
        if (day == null) {
            return null;
        }
        LocalDate date = from.with(TemporalAdjusters.nextOrSame(day));
        for (int i = 0; i < 104 && recessWeeks.contains(weekStart(date)); i++) {
            date = date.plusWeeks(1);
        }
        return date;
    }

    /** 그 달의 평의회 월례회의 날짜. 예: 셋째 주 일요일 = 그 달 세 번째 일요일. 5 는 마지막 주. */
    public static LocalDate councilDateIn(YearMonth month, int weekOfMonth, DayOfWeek day) {
        LocalDate first = month.atDay(1);
        return weekOfMonth >= LAST_WEEK
                ? first.with(TemporalAdjusters.lastInMonth(day))
                : first.with(TemporalAdjusters.dayOfWeekInMonth(weekOfMonth, day));
    }

    /** [from] 당일 또는 그 뒤 첫 평의회 월례회의 날짜. 일정이 없으면 null. */
    public static LocalDate nextCouncilMeeting(Integer weekOfMonth, DayOfWeek day, LocalDate from) {
        if (weekOfMonth == null || day == null) {
            return null;
        }
        YearMonth month = YearMonth.from(from);
        LocalDate date = councilDateIn(month, weekOfMonth, day);
        return date.isBefore(from) ? councilDateIn(month.plusMonths(1), weekOfMonth, day) : date;
    }

    public static String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    /** "셋째 주 일요일", "마지막 주 금요일" */
    public static String councilLabel(Integer weekOfMonth, DayOfWeek day) {
        if (weekOfMonth == null || day == null) {
            return null;
        }
        String week = switch (weekOfMonth) {
            case 1 -> "첫째 주";
            case 2 -> "둘째 주";
            case 3 -> "셋째 주";
            case 4 -> "넷째 주";
            default -> "마지막 주";
        };
        return week + " " + dayLabel(day);
    }
}
