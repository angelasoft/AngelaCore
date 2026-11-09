package com.angelasoft.presidium.attendance;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.attendance.AttendanceService.Overview;
import com.angelasoft.presidium.attendance.AttendanceService.SaveRequest;
import com.angelasoft.presidium.attendance.AttendanceService.SessionDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 출석부 API (인증 필요). kind = PRESIDIUM(주회합) | COUNCIL(평의회).
 *
 *   GET /app/presidiums/me/attendance?kind=          열림 여부, 오늘 명단, 이번 달 집계, 지난 출석부
 *   PUT /app/presidiums/me/attendance/today?kind=    {presentUserIds}  4간부만, 회합 당일 회합 시간부터 자정 전까지
 *   GET /app/presidiums/me/attendance/{sessionId}    지난 출석부 명단
 */
@RestController
@RequestMapping("/app/presidiums/me/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping
    public Overview overview(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam AttendanceKind kind
    ) {
        return attendanceService.overview(principal.getUserId(), kind);
    }

    @PutMapping("/today")
    public Overview save(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam AttendanceKind kind,
            @RequestBody SaveRequest request
    ) {
        return attendanceService.save(principal.getUserId(), kind, request);
    }

    @GetMapping("/{sessionId}")
    public SessionDetail detail(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long sessionId
    ) {
        return attendanceService.detail(principal.getUserId(), sessionId);
    }
}
