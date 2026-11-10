package com.angelasoft.admin.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 앱 갱신 토큰. 원문은 앱만 갖고 서버는 SHA-256 해시만 저장한다.
 * 한 번 쓰면 새 토큰으로 바뀌고(회전), 바뀐 옛 토큰이 다시 쓰이면 탈취로 보고 그 사용자의 토큰을 모두 끊는다.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회원 데이터를 지워도 토큰 때문에 막히지 않게 외래 키 없이 둔다. 갱신할 때 회원이 있는지 확인한다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;

    /** 회전으로 끊겼으면 true, 로그아웃·탈취 대응으로 끊겼으면 false. */
    private Boolean rotated;

    RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    boolean wasRotated() {
        return Boolean.TRUE.equals(rotated);
    }

    void revoke(boolean byRotation) {
        if (revokedAt == null) {
            this.revokedAt = LocalDateTime.now();
            this.rotated = byRotation;
        }
    }
}
