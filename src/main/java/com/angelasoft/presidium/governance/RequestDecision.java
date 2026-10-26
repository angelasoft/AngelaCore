package com.angelasoft.presidium.governance;

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

/** 간부 한 명의 신청 승인/반려. */
@Entity
@Table(
        name = "request_decisions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "officer_user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequestDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private MembershipRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_user_id", nullable = false)
    private User officer;

    @Column(nullable = false)
    private boolean approve;

    @Column(nullable = false)
    private LocalDateTime decidedAt;

    @Builder
    private RequestDecision(MembershipRequest request, User officer, boolean approve) {
        this.request = request;
        this.officer = officer;
        this.approve = approve;
        this.decidedAt = LocalDateTime.now();
    }
}
