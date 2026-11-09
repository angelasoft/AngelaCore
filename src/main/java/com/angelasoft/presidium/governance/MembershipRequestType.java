package com.angelasoft.presidium.governance;

public enum MembershipRequestType {
    /** 행동단원 → 협조단원 전환 신청. */
    AUXILIARY("협조단원 신청"),
    /** 협조단원 → 행동단원 전환 신청. */
    ACTIVE("행동단원 신청"),
    /** 퇴단 신청. */
    WITHDRAWAL("퇴단 신청"),
    /** 활동보고 목록에 없는 활동 등록 요청. 승인되면 모든 쁘레시디움 목록에 추가된다. */
    ACTIVITY("활동 등록 요청");

    private final String label;

    MembershipRequestType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 내 등급·소속을 바꾸는 신청. 이런 신청은 한 번에 하나만 진행하고 간부는 할 수 없다. */
    public boolean changesMembership() {
        return this != ACTIVITY;
    }
}
