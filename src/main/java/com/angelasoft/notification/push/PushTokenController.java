package com.angelasoft.notification.push;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.governance.GovernanceException;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기기 푸시 토큰 등록 (인증 필요).
 *
 *   POST   /app/notifications/push-tokens          {token, platform: ANDROID|IOS}  로그인·토큰 갱신 때
 *   DELETE /app/notifications/push-tokens?token=   로그아웃 때
 */
@RestController
@RequestMapping("/app/notifications/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS");

    private final PushTokenRepository tokenRepository;

    public record RegisterRequest(String token, String platform) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void register(@AuthenticationPrincipal AdminUserDetails principal, @RequestBody RegisterRequest request) {
        String token = request.token() == null ? "" : request.token().trim();
        String platform = request.platform() == null ? "" : request.platform().trim().toUpperCase(Locale.ROOT);
        if (token.isEmpty() || token.length() > 512 || !PLATFORMS.contains(platform)) {
            throw GovernanceException.badRequest("푸시 토큰 정보가 올바르지 않습니다.");
        }
        tokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.assign(principal.getUserId(), platform),
                () -> tokenRepository.save(new PushToken(token, principal.getUserId(), platform)));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal AdminUserDetails principal, @RequestParam String token) {
        tokenRepository.deleteByUserIdAndToken(principal.getUserId(), token.trim());
    }
}
