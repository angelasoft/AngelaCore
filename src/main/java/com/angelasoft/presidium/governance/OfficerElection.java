package com.angelasoft.presidium.governance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.OfficerRole;
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
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 간부 이동 투표. 후보 1명에 대한 찬반 투표이며, 투표 시작 시점의 활동단원 수 과반이 찬성하면 임명된다.
 */
@Entity
@Table(name = "officer_elections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OfficerElection {

    static final Duration VOTING_PERIOD = Duration.ofDays(7);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfficerRole position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "started_by_user_id", nullable = false)
    private User startedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ElectionStatus status;

    /** 투표 시작 시점의 활동단원 수. 과반 기준이 된다. */
    @Column(nullable = false)
    private int eligibleVoterCount;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime closesAt;

    private LocalDateTime closedAt;

    @Builder
    private OfficerElection(Presidium presidium, OfficerRole position, User candidate, User startedBy, int eligibleVoterCount) {
        this.presidium = presidium;
        this.position = position;
        this.candidate = candidate;
        this.startedBy = startedBy;
        this.eligibleVoterCount = eligibleVoterCount;
        this.status = ElectionStatus.OPEN;
        this.createdAt = LocalDateTime.now();
        this.closesAt = this.createdAt.plus(VOTING_PERIOD);
    }

    /** 임명에 필요한 찬성 수 (과반). */
    public int requiredVotes() {
        return eligibleVoterCount / 2 + 1;
    }

    public boolean isOpen() {
        return status == ElectionStatus.OPEN;
    }

    public boolean isPastDeadline(LocalDateTime now) {
        return now.isAfter(closesAt);
    }

    void close(ElectionStatus result) {
        this.status = result;
        this.closedAt = LocalDateTime.now();
    }
}
