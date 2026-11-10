package com.angelasoft.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인가 요청 저장소. 쿠키에 저장하고, 서버 메모리에도 state 값으로 함께 기억한다.
 *
 * 카카오톡 앱으로 인증하면 카카오톡이 로그인을 시작한 인앱 브라우저가 아닌 다른 브라우저로 돌아올 수 있어
 * 쿠키가 없다. 그래도 콜백의 state 로 인가 요청과 모바일 로그인 여부를 찾아 앱으로 돌려보낸다.
 * (서버 한 대 기준. 재시작하면 진행 중인 로그인은 다시 시도해야 한다.)
 */
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180;
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final Map<String, Pending> pendingByState = new ConcurrentHashMap<>();

    @Value("${app.cookie.secure}")
    private boolean secureCookie;

    private record Pending(OAuth2AuthorizationRequest request, boolean mobile, Instant expiresAt) {
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        OAuth2AuthorizationRequest fromCookie = CookieUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
                .map(cookie -> CookieUtils.deserialize(cookie, OAuth2AuthorizationRequest.class))
                .orElse(null);
        if (fromCookie != null) {
            return fromCookie;
        }
        Pending pending = pending(request);
        return pending == null ? null : pending.request();
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }
        CookieUtils.addCookie(
                response,
                OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
                CookieUtils.serialize(authorizationRequest),
                COOKIE_EXPIRE_SECONDS,
                secureCookie
        );
        removeExpired();
        pendingByState.put(authorizationRequest.getState(), new Pending(
                authorizationRequest, OAuthLoginMode.isMobileStart(request), Instant.now().plus(STATE_TTL)));
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        String state = request.getParameter("state");
        Pending pending = state == null ? null : pendingByState.remove(state);
        if (pending != null && pending.mobile()) {
            OAuthLoginMode.markMobile(request);
        }
        return authorizationRequest;
    }

    private Pending pending(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null) {
            return null;
        }
        Pending pending = pendingByState.get(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            return null;
        }
        return pending;
    }

    private void removeExpired() {
        Instant now = Instant.now();
        pendingByState.values().removeIf(p -> p.expiresAt().isBefore(now));
    }
}
