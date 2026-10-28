package com.angelasoft.presidium.volunteer;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.ScheduleRequest;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.ScheduleResponse;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.SchedulePage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 쁘레시디움 봉사 일정 API (인증 필요).
 *
 *   GET    /app/presidiums/me/schedules/upcoming?limit=3     다가오는 일정 (가까운 순)
 *   GET    /app/presidiums/me/schedules/past?page=0&size=20  지난 일정 (최근 순, 무한 스크롤)
 *   GET    /app/presidiums/me/schedules/{id}
 *   POST   /app/presidiums/me/schedules                      4간부만
 *   PUT    /app/presidiums/me/schedules/{id}                 4간부만
 *   DELETE /app/presidiums/me/schedules/{id}                 4간부만
 */
@RestController
@RequestMapping("/app/presidiums/me/schedules")
@RequiredArgsConstructor
public class VolunteerScheduleController {

    private final VolunteerScheduleService scheduleService;

    @GetMapping("/upcoming")
    public List<ScheduleResponse> upcoming(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam(defaultValue = "3") int limit
    ) {
        return scheduleService.upcoming(principal.getUserId(), limit);
    }

    @GetMapping("/past")
    public SchedulePage past(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return scheduleService.past(principal.getUserId(), page, size);
    }

    @GetMapping("/{scheduleId}")
    public ScheduleResponse get(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable Long scheduleId) {
        return scheduleService.get(principal.getUserId(), scheduleId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody ScheduleRequest request
    ) {
        return scheduleService.create(principal.getUserId(), request);
    }

    @PutMapping("/{scheduleId}")
    public ScheduleResponse update(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long scheduleId,
            @RequestBody ScheduleRequest request
    ) {
        return scheduleService.update(principal.getUserId(), scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AdminUserDetails principal, @PathVariable Long scheduleId) {
        scheduleService.delete(principal.getUserId(), scheduleId);
    }
}
