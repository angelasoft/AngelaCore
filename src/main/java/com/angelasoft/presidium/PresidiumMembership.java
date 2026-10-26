package com.angelasoft.presidium;

import com.angelasoft.admin.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 기존 가입 행에 컬럼을 추가할 수 있도록 DB 에서는 null 을 허용하고, null 은 활동단원으로 본다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MemberGrade grade;

    /** 4간부 직책. 일반 단원은 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OfficerRole officerRole;

    @Builder
    private PresidiumMembership(User user, Presidium presidium, OfficerRole officerRole) {
        this.user = user;
        this.presidium = presidium;
        this.joinedAt = LocalDateTime.now();
        this.grade = MemberGrade.ACTIVE;
        this.officerRole = officerRole;
    }

    public MemberGrade getGrade() {
        return grade == null ? MemberGrade.ACTIVE : grade;
    }

    /** DB 에 저장된 그대로의 등급 (기능 도입 전 데이터는 null). */
    MemberGrade getRawGrade() {
        return grade;
    }

    public boolean isOfficer() {
        return officerRole != null;
    }

    public void changeGrade(MemberGrade grade) {
        this.grade = grade;
    }

    public void appoint(OfficerRole role) {
        this.officerRole = role;
    }

    public void dismissOfficer() {
        this.officerRole = null;
    }
}
