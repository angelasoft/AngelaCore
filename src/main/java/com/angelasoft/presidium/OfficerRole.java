package com.angelasoft.presidium;

/** 쁘레시디움 4간부. 한 쁘레시디움에 직책별 1명, 한 사람은 한 직책만 맡는다. */
public enum OfficerRole {
    PRESIDENT("단장"),
    VICE_PRESIDENT("부단장"),
    SECRETARY("서기"),
    TREASURER("회계");

    private final String label;

    OfficerRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
