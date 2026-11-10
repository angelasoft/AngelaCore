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

    /** 신청 시점에 승인할 수 있는 간부 수. 과반 기준이 된다. */
    @Column(nullable = false)
    private int officerCount;

    /** 기능 도입 전 신청은 DB 에 null 이며 4간부 과반으로 본다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RequestApprover approver;

    /** 활동 등록 요청([MembershipRequestType#ACTIVITY])일 때 등록할 활동 이름. */
    @Column(length = 50)
    private String activityName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime decidedAt;

    @Builder
    private MembershipRequest(
            Presidium presidium, User requester, MembershipRequestType type, int officerCount, String activityName,
            RequestApprover approver) {
        this.presidium = presidium;
        this.requester = requester;
        this.type = type;
        this.officerCount = officerCount;
        this.activityName = activityName;
        this.approver = approver == null ? RequestApprover.OFFICERS : approver;
        this.status = MembershipRequestStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /** 처리에 필요한 승인 수 (간부 과반). */
    public int requiredApprovals() {
        return officerCount / 2 + 1;
    }

    public RequestApprover getApprover() {
        return approver == null ? RequestApprover.OFFICERS : approver;
    }

    public boolean isPending() {
        return status == MembershipRequestStatus.PENDING;
    }

    void finish(MembershipRequestStatus result) {
        this.status = result;
        this.decidedAt = LocalDateTime.now();
    }
}
