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

/** 간부 투표의 한 표. 한 사람은 한 투표에 한 번만 투표한다. */
@Entity
@Table(
        name = "election_ballots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"election_id", "voter_user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ElectionBallot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private OfficerElection election;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_user_id", nullable = false)
    private User voter;

    @Column(nullable = false)
    private boolean approve;

    @Column(nullable = false)
    private LocalDateTime castAt;

    @Builder
    private ElectionBallot(OfficerElection election, User voter, boolean approve) {
        this.election = election;
        this.voter = voter;
        this.approve = approve;
        this.castAt = LocalDateTime.now();
    }
}
