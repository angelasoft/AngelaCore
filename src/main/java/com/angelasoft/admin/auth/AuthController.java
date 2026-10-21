package com.angelasoft.admin.auth;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.admin.security.JwtProvider;
import com.angelasoft.admin.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    /** 설정 기반 단일 관리자는 users 테이블에 없으므로 JWT subject 로 0 을 쓴다. */
    private static final long ADMIN_USER_ID = 0L;
    private static final String ADMIN_DISPLAY_NAME = "관리자";

    private final AdminCredentials adminCredentials;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final JwtProvider jwtProvider;

    @Value("${app.jwt.cookie-name}")
    private String accessTokenCookieName;

    @Value("${app.cookie.secure}")
    private boolean secureCookie;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = httpRequest.getRemoteAddr();
        if (loginAttemptLimiter.isBlocked(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "TOO_MANY_ATTEMPTS"));
        }

        if (!adminCredentials.matches(request.username(), request.password())) {
            loginAttemptLimiter.recordFailure(clientIp);
            log.warn("[AdminLogin] 로그인 실패 - ip={}", clientIp);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "INVALID_CREDENTIALS"));
        }

        loginAttemptLimiter.reset(clientIp);
        log.info("[AdminLogin] 로그인 성공 - ip={}", clientIp);
        String token = jwtProvider.createAccessToken(ADMIN_USER_ID, null, ADMIN_DISPLAY_NAME, UserRole.ADMIN);
        return ResponseEntity.ok(Map.of("token", token, "displayName", ADMIN_DISPLAY_NAME));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminUserDetails principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        return ResponseEntity.ok(Map.of(
                "id", principal.getUserId(),
                "email", principal.getEmail() == null ? "" : principal.getEmail(),
                "name", principal.getName(),
                "role", principal.getRole().name()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie deleteCookie = ResponseCookie.from(accessTokenCookieName, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "LOGOUT_SUCCESS"));
    }
}
