package com.angelasoft.presidium;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.PresidiumProfileService.ProfileResponse;
import com.angelasoft.presidium.PresidiumProfileService.RecessRequest;
import com.angelasoft.presidium.PresidiumProfileService.UpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쁘레시디움 정보 API (인증 필요). 수정은 4간부만.
 *
 *   GET    /app/presidiums/me/profile                 정보, 휴회 주 목록, 이번 주 주회합 차수
 *   PUT    /app/presidiums/me/profile                 {foundedDate, approvedDate, meetingDay, meetingTime, meetingPlace, meetingNumberOffset}
 *   POST   /app/presidiums/me/profile/recesses        {date, reason}  date 가 든 주를 휴회로 등록
 *   DELETE /app/presidiums/me/profile/recesses/{id}
 */
@RestController
@RequestMapping("/app/presidiums/me/profile")
@RequiredArgsConstructor
public class PresidiumProfileController {

    private final PresidiumProfileService profileService;

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal AdminUserDetails principal) {
        return profileService.get(principal.getUserId());
    }

    @PutMapping
    public ProfileResponse update(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody UpdateRequest request
    ) {
        return profileService.update(principal.getUserId(), request);
    }

    @PostMapping("/recesses")
    public ProfileResponse addRecess(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody RecessRequest request
    ) {
        return profileService.addRecess(principal.getUserId(), request);
    }

    @DeleteMapping("/recesses/{recessId}")
    public ProfileResponse removeRecess(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long recessId
    ) {
        return profileService.removeRecess(principal.getUserId(), recessId);
    }
}
