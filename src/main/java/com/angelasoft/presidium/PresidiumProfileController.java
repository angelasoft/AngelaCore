package com.angelasoft.presidium;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.PresidiumProfileService.ProfileResponse;
import com.angelasoft.presidium.PresidiumProfileService.UpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쁘레시디움 정보 API (인증 필요).
 *
 *   GET /app/presidiums/me/profile   설립일·승인일·주회합 요일/시간/장소, 이번 주 주회합 차수
 *   PUT /app/presidiums/me/profile   {foundedDate, approvedDate, meetingDay, meetingTime, meetingPlace}  4간부만
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
}
