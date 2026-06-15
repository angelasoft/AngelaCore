package com.angelasoft.presidium;

import com.angelasoft.admin.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 ↔ 쁘레시디움 가입 관계. 1인 1쁘레시디움. */
@Entity
@Table(
        name = "presidium_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PresidiumMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Builder
    private PresidiumMembership(User user, Presidium presidium) {
        this.user = user;
        this.presidium = presidium;
        this.joinedAt = LocalDateTime.now();
    }

    void changePresidium(Presidium presidium) {
        this.presidium = presidium;
        this.joinedAt = LocalDateTime.now();
    }
}
