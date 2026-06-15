package com.angelasoft.admin.auth;

import java.util.Map;

@SuppressWarnings("unchecked")
public class NaverOAuth2UserInfo implements OAuth2UserInfo {

    private final Map<String, Object> attributes;
    private final Map<String, Object> response;

    public NaverOAuth2UserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        this.response = (Map<String, Object>) attributes.get("response");
    }

    @Override
    public String getProviderId() {
        return String.valueOf(response.get("id"));
    }

    @Override
    public String getName() {
        return "";
    }

    @Override
    public String getEmail() {
        return String.valueOf(response.get("email"));
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
