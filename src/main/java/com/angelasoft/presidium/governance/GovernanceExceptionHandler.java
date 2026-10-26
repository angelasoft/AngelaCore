package com.angelasoft.presidium.governance;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 앱이 그대로 보여줄 수 있도록 {"message": ...} 형태로 오류를 돌려준다. */
@RestControllerAdvice(assignableTypes = PresidiumGovernanceController.class)
class GovernanceExceptionHandler {

    @ExceptionHandler(GovernanceException.class)
    ResponseEntity<Map<String, String>> handle(GovernanceException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> handleBadBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 형식이 올바르지 않습니다."));
    }
}
