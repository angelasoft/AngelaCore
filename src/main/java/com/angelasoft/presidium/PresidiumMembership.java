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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 ↔ 쁘레시디움 가입 관계. 쁘레시디움마다 한 번씩 가입하며,
 * 어른 쁘레시디움은 1곳, 소년소녀 쁘레시디움은 여러 곳에 가입할 수 있다 (PresidiumMembershipRules).
 */
@Entity
@Table(
        name = "presidium_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_presidium_memberships_presidium_user",
                columnNames = {"presidium_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PresidiumMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    /** 기존 가입 행에 컬럼을 추가할 수 있도록 DB 에서는 null 을 허용하고, null 은 행동단원으로 본다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MemberGrade grade;

    /** 4간부 직책. 일반 단원은 null. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OfficerRole officerRole;

    /** 간부 임명일. 기능 도입 전 간부나 쁘레시디움을 만든 단장은 4간부가 입력하기 전까지 null. */
    private LocalDate officerAppointedDate;

    /** 간부 임기 (개월). 3년이면 36. */
    private Integer officerTermMonths;

    /** 임기 만료 한 달 전 간부 이동 알림을 이번 임기에 이미 보냈는지. */
    private Boolean officerTermNoticeSent;

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
        appoint(role, null, null);
    }

    public void appoint(OfficerRole role, LocalDate appointedDate, Integer termMonths) {
        this.officerRole = role;
        updateOfficerTerm(appointedDate, termMonths);
    }

    /** 임명일·임기를 바꾸면 만료 알림을 다시 보낼 수 있다. */
    public void updateOfficerTerm(LocalDate appointedDate, Integer termMonths) {
        this.officerAppointedDate = appointedDate;
        this.officerTermMonths = termMonths;
        this.officerTermNoticeSent = false;
    }

    public void dismissOfficer() {
        this.officerRole = null;
        this.officerAppointedDate = null;
        this.officerTermMonths = null;
        this.officerTermNoticeSent = null;
    }

    /** 임기 마지막 날. 임명일·임기가 없으면 null. 예: 2026-01-07 부터 3년이면 2029-01-06. */
    public LocalDate getOfficerTermEndDate() {
        if (officerRole == null || officerAppointedDate == null || officerTermMonths == null) {
            return null;
        }
        return officerAppointedDate.plusMonths(officerTermMonths).minusDays(1);
    }

    public boolean isOfficerTermNoticeSent() {
        return Boolean.TRUE.equals(officerTermNoticeSent);
    }

    public void markOfficerTermNoticeSent() {
        this.officerTermNoticeSent = true;
    }
}
