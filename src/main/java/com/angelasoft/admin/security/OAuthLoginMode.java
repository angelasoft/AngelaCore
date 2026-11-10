package com.angelasoft.admin.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 소셜 로그인이 앱(모바일)에서 시작됐는지 판단한다. 앱이면 결과를 angela:// 딥링크로, 아니면 관리자 웹으로 보낸다.
 *
 * 쿠키(ANGELA_OAUTH_MODE=mobile)가 기본이고, 카카오톡처럼 다른 브라우저로 돌아와 쿠키가 없으면
 * 인가 요청 저장소가 state 로 찾아 둔 표시를 쓴다.
 */
public final class OAuthLoginMode {

    public static final String COOKIE = "ANGELA_OAUTH_MODE";
    public static final String MOBILE = "mobile";
    private static final String MOBILE_ATTRIBUTE = OAuthLoginMode.class.getName() + ".mobile";

    private OAuthLoginMode() {
    }

    /** 인가 요청을 만들 때: 앱이 붙인 ?mode=mobile 또는 모드 쿠키. */
    static boolean isMobileStart(HttpServletRequest request) {
        return MOBILE.equals(request.getParameter("mode")) || hasMobileCookie(request);
    }

    static void markMobile(HttpServletRequest request) {
        request.setAttribute(MOBILE_ATTRIBUTE, Boolean.TRUE);
    }

    /** 콜백을 처리할 때. */
    public static boolean isMobile(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(MOBILE_ATTRIBUTE)) || hasMobileCookie(request);
    }

    /**
     * 실패했는데 어디서 시작했는지 모를 때(인가 요청을 찾지 못한 경우 등): 휴대폰 브라우저면 앱으로 돌려보낸다.
     * 관리자 웹은 PC 에서 쓴다.
     */
    public static boolean isLikelyMobile(HttpServletRequest request) {
        if (isMobile(request)) {
            return true;
        }
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && (userAgent.contains("Android") || userAgent.contains("iPhone")
                || userAgent.contains("iPad"));
    }

    private static boolean hasMobileCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE.equals(cookie.getName()) && MOBILE.equals(cookie.getValue())) {
                return true;
            }
        }
        return false;
    }
}
