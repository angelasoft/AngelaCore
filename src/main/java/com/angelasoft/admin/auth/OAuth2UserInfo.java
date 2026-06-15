package com.angelasoft.admin.auth;

import java.util.Map;

public interface OAuth2UserInfo {

    String getProviderId();

    String getName();

    String getEmail();

    Map<String, Object> getAttributes();
}
