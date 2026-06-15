package com.angelasoft.admin.auth;

import com.angelasoft.admin.security.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    /** 모바일 OAuth 시작 시 심어두는 모드 쿠키 이름. */
    public static final String OAUTH_MODE_COOKIE = "ANGELA_OAUTH_MODE";
    private static final String MOBILE_MODE = "mobile";

    private final JwtProvider jwtProvider;

    @Value("${app.jwt.cookie-name}")
    private String accessTokenCookieName;

    @Value("${app.jwt.access-token-expiration-seconds}")
    private long accessTokenExpiresInSeconds;

    @Value("${app.mobile.oauth-redirect-uri}")
    private String mobileRedirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String accessToken = jwtProvider.createAccessToken(
                principal.getUserId(),
                principal.getEmail(),
                principal.getName(),
                principal.getRole()
        );

        boolean mobile = isMobileRequest(request);
        log.info("[OAuth2] 인증 성공 - user={}, mobile={}", principal.getEmail(), mobile);

        if (mobile) {
            handleMobileSuccess(request, response, principal, accessToken);
            return;
        }

        ResponseCookie cookie = ResponseCookie.from(accessTokenCookieName, accessToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(accessTokenExpiresInSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        getRedirectStrategy().sendRedirect(request, response, "/admin");
    }

    private void handleMobileSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal principal,
            String accessToken
    ) throws IOException {
        // 일회용 모드 쿠키 제거
        ResponseCookie clear = ResponseCookie.from(OAUTH_MODE_COOKIE, "")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());

        String redirect = UriComponentsBuilder.fromUriString(mobileRedirectUri)
                .queryParam("token", accessToken)
                .queryParam("registered", principal.isAppRegistered())
                .queryParam("email", principal.getEmail())
                .build()
                .encode()
                .toUriString();

        log.info("[OAuth2] 모바일 딥링크로 리다이렉트: {}", redirect);
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    private boolean isMobileRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            log.info("[OAuth2] 쿠키 없음 → 웹 모드로 처리");
            return false;
        }
        for (Cookie cookie : cookies) {
            log.debug("[OAuth2] 쿠키 확인: {}={}", cookie.getName(), cookie.getValue());
            if (OAUTH_MODE_COOKIE.equals(cookie.getName()) && MOBILE_MODE.equals(cookie.getValue())) {
                return true;
            }
        }
        log.info("[OAuth2] ANGELA_OAUTH_MODE 쿠키 없음 → 웹 모드로 처리");
        return false;
    }
}
