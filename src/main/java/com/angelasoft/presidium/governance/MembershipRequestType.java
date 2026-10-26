package com.angelasoft.presidium.governance;

public enum MembershipRequestType {
    /** 활동단원 → 협조단원 전환 신청. */
    AUXILIARY("협조단원 신청"),
    /** 퇴단 신청. */
    WITHDRAWAL("퇴단 신청");

    private final String label;

    MembershipRequestType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
