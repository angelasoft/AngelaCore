package com.angelasoft.activity.report;

import java.time.LocalDate;
import java.util.List;

public final class ActivityReportDtos {

    private ActivityReportDtos() {
    }

    public record SubmitItem(Long activityId, Integer count) {
    }

    public record SubmitRequest(List<SubmitItem> items) {
    }

    public record SubmitResponse(Long id, LocalDate reportDate, int totalCount) {
    }

    public record ActivityCount(Long activityId, String name, long count) {
    }

    public record MemberCount(Long userId, String displayName, long count) {
    }

    /**
     * 한 달 통계. userId 가 있으면 그 단원 한 명의 통계이고 members 는 비어 있다.
     * month 는 "2026-10" 형식.
     */
    public record MonthlyStats(
            String month,
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
