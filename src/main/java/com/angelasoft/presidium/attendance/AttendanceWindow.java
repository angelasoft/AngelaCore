package com.angelasoft.presidium.attendance;

import com.angelasoft.presidium.MeetingCalendar;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;

/**
 * 출석부가 열리는 때. 회합 당일 회합 시간부터 그날 자정 전까지 입력할 수 있다.
 *
 * @param configured  회합 요일·시간이 입력돼 있는지
 * @param open        지금 입력할 수 있는지
 * @param meetingDate 열려 있으면 오늘, 아니면 다음 회합 날짜 (일정이 없으면 null)
 */
record AttendanceWindow(boolean configured, boolean open, LocalDate meetingDate, LocalTime meetingTime) {

    static AttendanceWindow presidium(
            DayOfWeek day, LocalTime time, Collection<LocalDate> recessWeeks, LocalDateTime now) {
        if (day == null || time == null) {
            return new AttendanceWindow(false, false, null, null);
        }
        return at(MeetingCalendar.nextPresidiumMeeting(day, recessWeeks, now.toLocalDate()), time, now);
    }

    static AttendanceWindow council(Integer weekOfMonth, DayOfWeek day, LocalTime time, LocalDateTime now) {
        if (weekOfMonth == null || day == null || time == null) {
            return new AttendanceWindow(false, false, null, null);
        }
        return at(MeetingCalendar.nextCouncilMeeting(weekOfMonth, day, now.toLocalDate()), time, now);
    }

    private static AttendanceWindow at(LocalDate meetingDate, LocalTime time, LocalDateTime now) {
        boolean open = meetingDate.equals(now.toLocalDate()) && !now.toLocalTime().isBefore(time);
        return new AttendanceWindow(true, open, meetingDate, time);
    }
}
