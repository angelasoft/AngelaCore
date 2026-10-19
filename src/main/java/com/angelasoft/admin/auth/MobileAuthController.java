package com.angelasoft.admin.auth;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 모바일(AngelaMobile)용 인증 엔드포인트.
 *
 * 흐름:
 *   1) 앱이 인앱 브라우저로 {@code GET /app/auth/oauth/{kakao|naver}} 열기
 *      → 모드 쿠키를 심고 Spring 표준 OAuth2 인가 요청으로 리다이렉트
 *   2) 소셜 인증 완료 → 성공 핸들러가 {@code angela://oauth/callback?token=...&registered=...}
 *      딥링크로 리다이렉트 (모바일 모드일 때)
 *   3) 미가입(registered=false)이면 앱에서 회원가입 폼 작성 후
 *      {@code POST /app/auth/signup} (Bearer 토큰)으로 프로필 완성
 */
@Slf4j
@RestController
@RequestMapping("/app/auth")
@RequiredArgsConstructor
public class MobileAuthController {

    private final UserRepository userRepository;

    @Value("${app.cookie.secure}")
    private boolean secureCookie;

    @GetMapping("/oauth/{provider}")
    public void startOAuth(@PathVariable String provider, HttpServletResponse response) throws IOException {
        String registrationId = provider == null ? "" : provider.toLowerCase();
        if (!registrationId.equals("kakao") && !registrationId.equals("naver")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported provider");
            return;
        }

        ResponseCookie mode = ResponseCookie.from(OAuth2AuthenticationSuccessHandler.OAUTH_MODE_COOKIE, "mobile")
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(300)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, mode.toString());
        log.info("[MobileAuth] {} OAuth 시작 → ANGELA_OAUTH_MODE=mobile 쿠키 설정", registrationId);
        response.sendRedirect("/oauth2/authorization/" + registrationId);
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        return ResponseEntity.ok(toProfile(user));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(Authentication authentication, @RequestBody AppSignUpRequest request) {
        User user = currentUser(authentication);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        user.completeAppRegistration(
                request.name(),
                request.baptismalName(),
                request.phoneNumber(),
                request.birthDate(),
                request.gender()
        );
        userRepository.save(user);
        return ResponseEntity.ok(toProfile(user));
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AdminUserDetails principal)) {
            return null;
        }
        return userRepository.findById(principal.getUserId()).orElse(null);
    }

    private Map<String, Object> toProfile(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("email", user.getEmail());
        profile.put("name", user.getName());
        profile.put("role", user.getRole().name());
        profile.put("appRegistered", user.isAppRegistered());
        profile.put("baptismalName", user.getBaptismalName());
        profile.put("phoneNumber", user.getPhoneNumber());
        profile.put("birthDate", user.getBirthDate() == null ? null : user.getBirthDate().toString());
        profile.put("gender", user.getGender());
        return profile;
    }
}
