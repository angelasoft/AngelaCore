package com.angelasoft.activity.report;

import java.time.LocalDate;

/** 활동보고 집계 쿼리 결과 형태. */
public final class ActivityReportProjections {

    private ActivityReportProjections() {
    }

    /** 활동별 합계. */
    public interface ActivityTotal {
        Long getActivityId();

        String getName();

        Long getTotal();
    }

    /** 단원별 합계. */
    public interface MemberTotal {
        Long getUserId();

        String getName();

        String getBaptismalName();

        Long getTotal();
    }

    /** 날짜별 합계 (월별 추이 계산용). */
    public interface DateTotal {
        LocalDate getReportDate();

        Long getTotal();
    }
}
