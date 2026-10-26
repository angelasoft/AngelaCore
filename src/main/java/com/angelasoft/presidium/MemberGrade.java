package com.angelasoft.presidium;

/** 쁘레시디움 단원 등급. */
public enum MemberGrade {
    ACTIVE("활동단원"),
    AUXILIARY("협조단원");

    private final String label;

    MemberGrade(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
