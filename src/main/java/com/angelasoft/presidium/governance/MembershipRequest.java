package com.angelasoft.presidium.governance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.Presidium;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 협조단원·퇴단 신청. 신청 시점에 임명된 간부 수의 과반이 승인하면 처리된다.
 */
@Entity
@Table(name = "membership_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRequestStatus status;

    /** 신청 시점에 임명된 간부 수. */
    @Column(nullable = false)
    private int officerCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    @Builder
    private MembershipRequest(Presidium presidium, User requester, MembershipRequestType type, int officerCount) {
        this.presidium = presidium;
        this.requester = requester;
        this.type = type;
        this.officerCount = officerCount;
        this.status = MembershipRequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /** 처리에 필요한 승인 수 (간부 과반). */
    public int requiredApprovals() {
        return officerCount / 2 + 1;
    }

    public boolean isPending() {
        return status == MembershipRequestStatus.PENDING;
    }

    void finish(MembershipRequestStatus result) {
        this.status = result;
        this.decidedAt = LocalDateTime.now();
    }
}
