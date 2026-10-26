package com.angelasoft.presidium.governance;

import org.springframework.http.HttpStatus;

/** 간부 투표·단원 신청 처리 중 사용자에게 그대로 보여줄 오류. */
public class GovernanceException extends RuntimeException {

    private final HttpStatus status;

    public GovernanceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static GovernanceException badRequest(String message) {
        return new GovernanceException(HttpStatus.BAD_REQUEST, message);
    }

    public static GovernanceException forbidden(String message) {
        return new GovernanceException(HttpStatus.FORBIDDEN, message);
    }

    public static GovernanceException notFound(String message) {
        return new GovernanceException(HttpStatus.NOT_FOUND, message);
    }

    public static GovernanceException conflict(String message) {
        return new GovernanceException(HttpStatus.CONFLICT, message);
    }
}
