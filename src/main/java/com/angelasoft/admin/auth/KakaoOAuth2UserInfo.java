package com.angelasoft.admin.auth;

import java.util.Map;

@SuppressWarnings("unchecked")
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> profile;
    private final Map<String, Object> account;

    public KakaoOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.account = (Map<String, Object>) attributes.getOrDefault("kakao_account", Map.of());
        this.profile = (Map<String, Object>) account.getOrDefault("profile", Map.of());
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id"));
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getEmail() {
        return String.valueOf(account.get("email"));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
