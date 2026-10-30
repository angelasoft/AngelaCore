package com.angelasoft.admin.user;

public enum AuthProvider {
    NAVER("네이버"),
    KAKAO("카카오");

    private final String label;

    AuthProvider(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
