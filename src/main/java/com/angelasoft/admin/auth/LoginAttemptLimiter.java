package com.angelasoft.admin.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 같은 IP에서 관리자 로그인이 연속 실패하면 일정 시간 차단한다. (서버 1대 기준 메모리 저장)
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    static final Duration BLOCK_WINDOW = Duration.ofMinutes(15);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempts current = attempts.get(key);
        if (current == null) {
            return false;
        }
        if (current.isExpired()) {
            attempts.remove(key, current);
            return false;
        }
        return current.count() >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.values().removeIf(Attempts::isExpired);
        }
        attempts.compute(key, (k, current) -> current == null || current.isExpired()
                ? new Attempts(1, Instant.now())
                : new Attempts(current.count() + 1, current.firstFailureAt()));
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    private record Attempts(int count, Instant firstFailureAt) {
        boolean isExpired() {
            return firstFailureAt.plus(BLOCK_WINDOW).isBefore(Instant.now());
        }
    }
}
