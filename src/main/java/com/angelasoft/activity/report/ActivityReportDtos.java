package com.angelasoft.activity.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ActivityReportDtos {

    private ActivityReportDtos() {
    }

    public record SubmitItem(Long activityId, Integer count) {
    }

    public record SubmitRequest(List<SubmitItem> items) {
    }

    /** 제출 결과. 보고한 횟수를 모두 0 으로 내려 이번 주 보고를 지웠으면 id·reportDate 는 null. */
    public record SubmitResponse(Long id, LocalDate reportDate, LocalDate weekStart, int totalCount) {
    }

    public record WeeklyItem(Long activityId, int count) {
    }

    /** 이번 주(weekStart 월요일 ~ weekEnd 일요일) 내 보고. 아직 보고하지 않았으면 updatedAt 은 null, items 는 빈 목록. */
    public record WeeklyReport(
            LocalDate weekStart,
            LocalDate weekEnd,
            LocalDateTime updatedAt,
            int totalCount,
            List<WeeklyItem> items
    ) {
    }

    public record ActivityCount(Long activityId, String name, long count) {
    }

    public record MemberCount(Long userId, String displayName, long count) {
    }

    /**
     * 한 달 또는 한 주 통계. userId 가 있으면 그 단원 한 명의 통계이고 members 는 비어 있다.
     * month 는 "2026-10" 형식(주 통계면 그 주 월요일이 속한 달), 주 통계일 때만 weekStart(월)·weekEnd(일)가 있다.
     */
    public record MonthlyStats(
            String month,
            LocalDate weekStart,
            LocalDate weekEnd,
            Long userId,
            long totalCount,
            List<ActivityCount> activities,
            List<MemberCount> members
    ) {
    }

    public record MonthCount(int month, long count) {
    }

    /** 한 해의 월별 합계 (1~12월). */
    public record YearlyTrend(int year, Long userId, List<MonthCount> months) {
    }
}
