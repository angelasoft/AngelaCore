package com.angelasoft.notification;

/** 앱 알림 종류. 앱은 이 값으로 알림을 눌렀을 때 이동할 화면을 정한다. */
public enum NotificationType {
    /** 협조단원·퇴단 신청 승인 요청 (4간부에게). */
    MEMBERSHIP_REQUEST,
    /** 신청 처리 결과 (신청자에게). */
    MEMBERSHIP_REQUEST_RESULT,
    /** 간부 투표 시작 (투표권자에게). */
    ELECTION_STARTED,
    /** 간부 투표 결과 (단원 전체에게). */
    ELECTION_RESULT
}
