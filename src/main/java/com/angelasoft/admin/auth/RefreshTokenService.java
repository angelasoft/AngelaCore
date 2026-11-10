package com.angelasoft.admin.auth;

import com.angelasoft.admin.security.JwtProvider;
import com.angelasoft.admin.user.User;
import com.angelasoft.admin.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 앱 로그인 유지. 접근 토큰(JWT)은 짧게, 갱신 토큰은 길게 두고 갱신할 때마다 새 갱신 토큰으로 바꾼다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Value("${app.jwt.refresh-token-expiration-days:60}")
    private long refreshTokenDays;

    /** 갱신 결과. registered 는 앱 회원가입(프로필 입력)을 마쳤는지. */
    public record Tokens(String accessToken, String refreshToken, boolean registered) {
    }

    /** 갱신 토큰이 없거나, 만료·로그아웃됐거나, 회원이 없을 때. */
    public static class InvalidRefreshTokenException extends RuntimeException {
        InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    /** 로그인 성공 시 새 갱신 토큰을 발급하고 원문을 돌려준다. */
    @Transactional
    public String issue(Long userId) {
        String token = newToken();
        repository.save(new RefreshToken(userId, hash(token), LocalDateTime.now().plusDays(refreshTokenDays)));
        return token;
    }

    /**
     * 갱신 토큰으로 새 접근 토큰과 새 갱신 토큰을 받는다. 쓴 갱신 토큰은 끊긴다.
     * 이미 바뀐 옛 토큰이 다시 오면 누군가 훔쳐 쓴 것으로 보고 그 사용자의 갱신 토큰을 모두 끊는다.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Tokens rotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("갱신 토큰이 없습니다.");
        }
        RefreshToken stored = repository.findByTokenHash(hash(refreshToken.strip()))
                .orElseThrow(() -> new InvalidRefreshTokenException("알 수 없는 갱신 토큰입니다."));
        LocalDateTime now = LocalDateTime.now();
        if (!stored.isActive(now)) {
            if (stored.getRevokedAt() != null && stored.wasRotated()) {
                log.warn("[RefreshToken] 이미 바뀐 갱신 토큰 재사용 → user {} 의 갱신 토큰을 모두 끊습니다", stored.getUserId());
                repository.findByUserIdAndRevokedAtIsNull(stored.getUserId()).forEach(t -> t.revoke(false));
            }
            throw new InvalidRefreshTokenException("만료되었거나 사용할 수 없는 갱신 토큰입니다.");
        }
        User user = userRepository.findById(stored.getUserId()).orElse(null);
        if (user == null) {
            stored.revoke(false);
            throw new InvalidRefreshTokenException("회원 정보를 찾을 수 없습니다.");
        }

        stored.revoke(true);
        String next = issue(user.getId());
        String access = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getName(), user.getRole());
        return new Tokens(access, next, user.isAppRegistered());
    }

    /** 로그아웃: 이 기기의 갱신 토큰을 끊는다. 없거나 이미 끊긴 토큰이면 조용히 넘어간다. */
    @Transactional
    public void revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(refreshToken.strip())).ifPresent(t -> t.revoke(false));
    }

    /** 매일 새벽: 만료된 지 일주일 지난 토큰을 지운다 (재사용 감지를 위해 바로 지우지는 않는다). */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpired() {
        int deleted = repository.deleteExpiredBefore(LocalDateTime.now().minusDays(7));
        if (deleted > 0) {
            log.info("[RefreshToken] 만료 토큰 {}건 정리", deleted);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
