package com.angelasoft.admin.auth;

import com.angelasoft.admin.security.AdminUserDetails;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Value("${app.jwt.cookie-name}")
    private String accessTokenCookieName;

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminUserDetails principal)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        return ResponseEntity.ok(Map.of(
                "id", principal.getUserId(),
                "email", principal.getEmail(),
                "name", principal.getName(),
                "role", principal.getRole().name()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie deleteCookie = ResponseCookie.from(accessTokenCookieName, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body(Map.of("message", "LOGOUT_SUCCESS"));
    }
}
