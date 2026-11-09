package com.angelasoft.presidium.attendance;

/** 출석부 종류. */
public enum AttendanceKind {
    /** 주회합. 행동단원(쁘레또리움 포함, 간부 포함)이 대상. */
    PRESIDIUM("주회합"),
    /** 평의회(Cu) 월례회의. Pr 4간부가 대상. */
    COUNCIL("평의회");

    private final String label;

    AttendanceKind(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
