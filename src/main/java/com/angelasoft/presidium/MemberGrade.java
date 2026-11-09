package com.angelasoft.presidium;

/**
 * 쁘레시디움 단원 등급. 행동단원·협조단원 두 갈래이고, 각각 단장이 임명하는 높은 등급이 있다.
 *
 * - 쁘레또리움 단원: 행동단원 + 매일 뗏세라 기도 전부, 미사·영성체, 성무일도
 * - 아듀또리움 단원: 협조단원 + 매일 미사·영성체, 성무일도
 */
public enum MemberGrade {
    ACTIVE("행동단원", true, false),
    PRAETORIAN("쁘레또리움 단원", true, true),
    AUXILIARY("협조단원", false, false),
    ADJUTORIAN("아듀또리움 단원", false, true);

    private final String label;
    private final boolean activeMember;
    private final boolean higherDegree;

    MemberGrade(String label, boolean activeMember, boolean higherDegree) {
        this.label = label;
        this.activeMember = activeMember;
        this.higherDegree = higherDegree;
    }

    public String getLabel() {
        return label;
    }

    /** 행동단원 갈래(행동단원·쁘레또리움): 간부 투표에 참여하고 간부를 맡을 수 있다. */
    public boolean isActiveMember() {
        return activeMember;
    }

    /** 단장이 임명한 높은 등급(쁘레또리움·아듀또리움). */
    public boolean isHigherDegree() {
        return higherDegree;
    }

    /** 같은 갈래에서 높은 등급(true) 또는 기본 등급(false). */
    public MemberGrade withHigherDegree(boolean higher) {
        if (activeMember) {
            return higher ? PRAETORIAN : ACTIVE;
        }
        return higher ? ADJUTORIAN : AUXILIARY;
    }
}
