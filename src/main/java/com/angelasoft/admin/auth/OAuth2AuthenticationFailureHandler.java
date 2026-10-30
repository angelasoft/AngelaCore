package com.angelasoft.admin.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OAuth2 인증 실패 시 모바일/웹 모드를 구분해 적절히 리다이렉트한다.
 */
@Slf4j
@Component
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.mobile.oauth-redirect-uri}")
    private String mobileRedirectUri;

    @Value("${app.cookie.secure}")
    private boolean secureCookie;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        if (!isMobileRequest(request)) {
            super.onAuthenticationFailure(request, response, exception);
            return;
        }

        String reason = exception.getMessage() == null ? "OAuth authentication failed" : exception.getMessage();
        String errorCode = exception instanceof OAuth2AuthenticationException oauth
                && CustomOAuth2UserService.PROVIDER_MISMATCH.equals(oauth.getError().getErrorCode())
                ? CustomOAuth2UserService.PROVIDER_MISMATCH
                : "oauth_failed";
        log.warn("[OAuth2] 모바일 인증 실패({}): {}", errorCode, reason);

        ResponseCookie clear = ResponseCookie.from(OAuth2AuthenticationSuccessHandler.OAUTH_MODE_COOKIE, "")
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());

        String redirect = UriComponentsBuilder.fromUriString(mobileRedirectUri)
                .queryParam("error", errorCode)
                .queryParam("error_description", reason)
                .build()
                .encode()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, redirect);
    }

    private boolean isMobileRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (OAuth2AuthenticationSuccessHandler.OAUTH_MODE_COOKIE.equals(cookie.getName())
                    && "mobile".equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }
}
