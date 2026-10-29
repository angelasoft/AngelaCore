package com.angelasoft.activity.report;

import com.angelasoft.activity.report.ActivityReportDtos.MonthlyStats;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitRequest;
import com.angelasoft.activity.report.ActivityReportDtos.SubmitResponse;
import com.angelasoft.activity.report.ActivityReportDtos.WeeklyReport;
import com.angelasoft.activity.report.ActivityReportDtos.YearlyTrend;
import com.angelasoft.admin.security.AdminUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 활동보고 제출·통계 API (인증 필요).
 *
 *   POST /app/presidiums/me/activity-reports                         {items: [{activityId, count}]} 이번 주 보고를 덮어씀
 *   GET  /app/presidiums/me/activity-reports/current-week            이번 주(월~일) 내 보고
 *   GET  /app/presidiums/me/activity-stats?month=2026-10&userId=     한 달 활동별 합계 (+ 단원별 합계)
 *   GET  /app/presidiums/me/activity-stats?week=2026-10-26&userId=   그 날짜가 속한 주(월~일) 합계
 *   GET  /app/presidiums/me/activity-stats/yearly?year=2026&userId=  월별 합계
 */
@RestController
@RequestMapping("/app/presidiums/me")
@RequiredArgsConstructor
public class ActivityReportController {

    private final ActivityReportService reportService;

    @PostMapping("/activity-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmitResponse submit(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody SubmitRequest request
    ) {
        return reportService.submit(principal.getUserId(), request.items());
    }

    @GetMapping("/activity-reports/current-week")
    public WeeklyReport currentWeek(@AuthenticationPrincipal AdminUserDetails principal) {
        return reportService.currentWeek(principal.getUserId());
    }

    @GetMapping("/activity-stats")
    public MonthlyStats monthly(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String week,
            @RequestParam(required = false) Long userId
    ) {
        return reportService.monthly(principal.getUserId(), month, week, userId);
    }

    @GetMapping("/activity-stats/yearly")
    public YearlyTrend yearly(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long userId
    ) {
        return reportService.yearly(principal.getUserId(), year, userId);
    }
}
