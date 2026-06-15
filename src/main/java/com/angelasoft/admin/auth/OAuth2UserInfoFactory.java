package com.angelasoft.admin.auth;

import com.angelasoft.admin.user.AuthProvider;
import java.util.Map;

public final class OAuth2UserInfoFactory {

    private OAuth2UserInfoFactory() {
    }

    public static OAuth2UserInfo create(AuthProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case NAVER -> new NaverOAuth2UserInfo(attributes);
            case KAKAO -> new KakaoOAuth2UserInfo(attributes);
        };
    }
}
