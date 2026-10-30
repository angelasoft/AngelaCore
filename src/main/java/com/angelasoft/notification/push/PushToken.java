package com.angelasoft.notification.push;

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

/** 사용자 기기의 FCM 토큰. 한 기기(토큰)는 마지막으로 로그인한 사용자 한 명에게만 묶인다. */
@Entity
@Table(
        name = "push_tokens",
        indexes = @Index(name = "idx_push_tokens_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 512)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** ANDROID | IOS */
    @Column(nullable = false, length = 10)
    private String platform;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    PushToken(String token, Long userId, String platform) {
        this.token = token;
        assign(userId, platform);
    }

    void assign(Long userId, String platform) {
        this.userId = userId;
        this.platform = platform;
        this.updatedAt = LocalDateTime.now();
    }
}
