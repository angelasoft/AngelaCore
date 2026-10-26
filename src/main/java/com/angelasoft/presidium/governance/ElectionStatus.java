package com.angelasoft.presidium.governance;

public enum ElectionStatus {
    /** 투표 진행 중. */
    OPEN,
    /** 과반 찬성으로 임명됨. */
    APPOINTED,
    /** 과반 찬성이 불가능해져 부결됨. */
    REJECTED,
    /** 7일 안에 결정되지 않아 종료됨. */
    EXPIRED,
    /** 후보가 퇴단하는 등으로 취소됨. */
    CANCELED
}
