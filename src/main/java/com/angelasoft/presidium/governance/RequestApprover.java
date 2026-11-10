package com.angelasoft.presidium.governance;

/** 신청을 승인하는 사람. */
public enum RequestApprover {
    /** 4간부 과반 (기본). */
    OFFICERS,
    /** 단장 혼자. 간부만 남은 쁘레시디움에서 단장 외 간부가 퇴단 신청할 때. */
    PRESIDENT,
    /** 신청한 간부를 뺀 나머지 간부 과반. 간부만 남았는데 단장이 공석일 때. */
    OTHER_OFFICERS
}
