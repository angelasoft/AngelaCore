package com.angelasoft.activity;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 활동보고 항목 조회 API.
 *
 *   GET /app/activities   사용 중인 활동 목록 (엑셀 보고서 순서)
 */
@RestController
@RequestMapping("/app/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityRepository activityRepository;

    @GetMapping
    public List<ActivityResponse> activities() {
        return activityRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(ActivityResponse::from)
                .toList();
    }

    public record ActivityResponse(Long id, String name) {
        static ActivityResponse from(Activity activity) {
            return new ActivityResponse(activity.getId(), activity.getName());
        }
    }
}
