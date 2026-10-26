package com.angelasoft.presidium.governance;

import static com.angelasoft.presidium.governance.GovernanceDtos.displayName;
import static com.angelasoft.presidium.governance.GovernanceDtos.toInstant;

import com.angelasoft.admin.user.User;
import com.angelasoft.notification.NotificationService;
import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.ElectionResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간부 이동 투표.
 *
 * - 4간부만 투표를 시작할 수 있고, 후보는 활동단원이어야 한다. 직책별로 진행 중인 투표는 하나.
 * - 투표 시작 전에 가입한 활동단원이 한 번씩 찬반 투표한다.
 * - 과반 찬성이면 임명, 과반 찬성이 불가능해지면 부결, 7일이 지나면 종료.
 */
@Service
@RequiredArgsConstructor
public class OfficerElectionService {

    private static final int CLOSED_HISTORY_SIZE = 10;

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final OfficerElectionRepository electionRepository;
    private final ElectionBallotRepository ballotRepository;
    private final NotificationService notificationService;

    @Transactional
    public List<ElectionResponse> list(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        closeExpired(presidiumId);

        List<OfficerElection> all = electionRepository.findByPresidiumIdWithUsers(presidiumId);
        List<OfficerElection> visible = new ArrayList<>(all.stream().filter(OfficerElection::isOpen).toList());
        all.stream().filter(e -> !e.isOpen()).limit(CLOSED_HISTORY_SIZE).forEach(visible::add);
        return visible.stream().map(e -> toResponse(e, me)).toList();
    }

    @Transactional
    public ElectionResponse start(Long userId, OfficerRole position, Long candidateUserId) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("4간부만 간부 이동 투표를 시작할 수 있습니다.");
        }
        if (position == null || candidateUserId == null) {
            throw GovernanceException.badRequest("직책과 후보를 선택해 주세요.");
        }

        Long presidiumId = me.getPresidium().getId();
        closeExpired(presidiumId);

        PresidiumMembership candidate = membershipRepository.findByPresidiumIdAndUserId(presidiumId, candidateUserId)
                .orElseThrow(() -> GovernanceException.notFound("후보가 이 쁘레시디움 단원이 아닙니다."));
        if (candidate.getGrade() != MemberGrade.ACTIVE) {
            throw GovernanceException.badRequest("간부는 활동단원만 맡을 수 있습니다.");
        }
        if (candidate.getOfficerRole() == position) {
            throw GovernanceException.conflict("이미 " + position.getLabel() + " 직책을 맡고 있는 단원입니다.");
        }
        if (electionRepository.existsByPresidiumIdAndPositionAndStatus(presidiumId, position, ElectionStatus.OPEN)) {
            throw GovernanceException.conflict(position.getLabel() + " 투표가 이미 진행 중입니다.");
        }

        List<Long> voterIds = membershipRepository.findByPresidiumIdWithUser(presidiumId).stream()
                .filter(m -> m.getGrade() == MemberGrade.ACTIVE)
                .map(m -> m.getUser().getId())
                .toList();

        OfficerElection election = electionRepository.save(OfficerElection.builder()
                .presidium(me.getPresidium())
                .position(position)
                .candidate(candidate.getUser())
                .startedBy(me.getUser())
                .eligibleVoterCount(voterIds.size())
                .build());

        notificationService.notify(
                voterIds.stream().filter(id -> !id.equals(userId)).toList(),
                NotificationType.ELECTION_STARTED,
                "간부 이동 투표",
                "%s 단원의 %s 임명 투표가 시작되었습니다. 투표에 참여해 주세요."
                        .formatted(displayName(candidate.getUser()), position.getLabel()),
                election.getId());

        return toResponse(election, me);
    }

    @Transactional
    public ElectionResponse vote(Long userId, Long electionId, Boolean approve) {
        if (approve == null) {
            throw GovernanceException.badRequest("찬성 또는 반대를 선택해 주세요.");
        }
        PresidiumMembership me = membershipLookup.require(userId);
        OfficerElection election = electionRepository.findById(electionId)
                .filter(e -> e.getPresidium().getId().equals(me.getPresidium().getId()))
                .orElseThrow(() -> GovernanceException.notFound("투표를 찾을 수 없습니다."));

        closeIfPastDeadline(election, LocalDateTime.now());
        if (!election.isOpen()) {
            throw GovernanceException.conflict("이미 종료된 투표입니다.");
        }
        String reason = cannotVoteReason(election, me);
        if (reason != null) {
            throw GovernanceException.forbidden(reason);
        }
        if (ballotRepository.existsByElectionIdAndVoterId(electionId, userId)) {
            throw GovernanceException.conflict("이미 투표했습니다.");
        }

        ballotRepository.save(ElectionBallot.builder()
                .election(election)
                .voter(me.getUser())
                .approve(approve)
                .build());
        evaluate(election);
        return toResponse(election, me);
    }

    /** 퇴단 등으로 후보가 사라진 진행 중 투표를 취소한다. */
    @Transactional
    public void cancelOpenElectionsForCandidate(Long candidateUserId) {
        for (OfficerElection election : electionRepository.findByCandidateIdAndStatus(candidateUserId, ElectionStatus.OPEN)) {
            finish(election, ElectionStatus.CANCELED);
        }
    }

    private void closeExpired(Long presidiumId) {
        LocalDateTime now = LocalDateTime.now();
        for (OfficerElection election : electionRepository.findByPresidiumIdAndStatus(presidiumId, ElectionStatus.OPEN)) {
            closeIfPastDeadline(election, now);
        }
    }

    private void closeIfPastDeadline(OfficerElection election, LocalDateTime now) {
        if (election.isOpen() && election.isPastDeadline(now)) {
            finish(election, ElectionStatus.EXPIRED);
        }
    }

    private void evaluate(OfficerElection election) {
        List<ElectionBallot> ballots = ballotRepository.findByElectionId(election.getId());
        long yes = ballots.stream().filter(ElectionBallot::isApprove).count();
        long no = ballots.size() - yes;
        int required = election.requiredVotes();

        if (yes >= required) {
            appoint(election);
        } else if (election.getEligibleVoterCount() - no < required) {
            finish(election, ElectionStatus.REJECTED);
        }
    }

    private void appoint(OfficerElection election) {
        Long presidiumId = election.getPresidium().getId();
        PresidiumMembership candidate = membershipRepository
                .findByPresidiumIdAndUserId(presidiumId, election.getCandidate().getId())
                .filter(m -> m.getGrade() == MemberGrade.ACTIVE)
                .orElse(null);
        if (candidate == null) {
            finish(election, ElectionStatus.CANCELED);
            return;
        }

        membershipRepository.findByPresidiumIdAndOfficerRole(presidiumId, election.getPosition())
                .filter(previous -> !previous.getId().equals(candidate.getId()))
                .ifPresent(PresidiumMembership::dismissOfficer);
        candidate.appoint(election.getPosition());
        finish(election, ElectionStatus.APPOINTED);
    }

    private void finish(OfficerElection election, ElectionStatus result) {
        election.close(result);

        String candidateName = displayName(election.getCandidate());
        String position = election.getPosition().getLabel();
        String message = switch (result) {
            case APPOINTED -> "%s 단원이 %s 임명되었습니다.".formatted(candidateName, withEuro(position));
            case REJECTED -> "%s 단원의 %s 임명 투표가 부결되었습니다.".formatted(candidateName, position);
            case EXPIRED -> "%s 단원의 %s 임명 투표가 기한 내에 결정되지 않아 종료되었습니다.".formatted(candidateName, position);
            case CANCELED -> "%s 단원의 %s 임명 투표가 취소되었습니다.".formatted(candidateName, position);
            case OPEN -> throw new IllegalArgumentException("OPEN is not a result");
        };

        List<Long> memberIds = membershipRepository.findByPresidiumIdWithUser(election.getPresidium().getId()).stream()
                .map(m -> m.getUser().getId())
                .toList();
        notificationService.notify(memberIds, NotificationType.ELECTION_RESULT, "간부 이동 투표 결과", message, election.getId());
    }

    /** 받침이 있으면(ㄹ 제외) "으로", 없으면 "로"를 붙인다. 예: 단장으로, 서기로. */
    static String withEuro(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return word + "로";
        }
        int jongseong = (last - '가') % 28;
        return word + (jongseong == 0 || jongseong == 8 ? "로" : "으로");
    }

    private String cannotVoteReason(OfficerElection election, PresidiumMembership me) {
        if (me.getGrade() != MemberGrade.ACTIVE) {
            return "활동단원만 투표할 수 있습니다.";
        }
        if (me.getJoinedAt().isAfter(election.getCreatedAt())) {
            return "투표가 시작된 뒤 가입한 단원은 이 투표에 참여할 수 없습니다.";
        }
        return null;
    }

    private ElectionResponse toResponse(OfficerElection election, PresidiumMembership me) {
        List<ElectionBallot> ballots = ballotRepository.findByElectionId(election.getId());
        long yes = ballots.stream().filter(ElectionBallot::isApprove).count();
        Boolean myVote = ballots.stream()
                .filter(b -> b.getVoter().getId().equals(me.getUser().getId()))
                .map(ElectionBallot::isApprove)
                .findFirst()
                .orElse(null);
        boolean canVote = election.isOpen() && myVote == null && cannotVoteReason(election, me) == null;
        User candidate = election.getCandidate();

        return new ElectionResponse(
                election.getId(),
                election.getPosition(),
                election.getPosition().getLabel(),
                candidate.getId(),
                displayName(candidate),
                displayName(election.getStartedBy()),
                election.getStatus(),
                election.getEligibleVoterCount(),
                election.requiredVotes(),
                yes,
                ballots.size() - yes,
                myVote,
                canVote,
                toInstant(election.getCreatedAt()),
                toInstant(election.getClosesAt()),
                toInstant(election.getClosedAt()));
    }
}
