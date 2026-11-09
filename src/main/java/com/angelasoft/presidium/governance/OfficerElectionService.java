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
import com.angelasoft.presidium.governance.GovernanceDtos.OfficerTermRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간부 이동 투표.
 *
 * - 4간부만 투표를 시작할 수 있고, 후보는 행동단원(쁘레또리움 포함)이어야 한다. 직책별로 진행 중인 투표는 하나.
 * - 투표 시작 전에 가입한 행동단원(쁘레또리움 포함)이 한 번씩 찬반 투표한다.
 * - 과반 찬성이면 당선, 과반 찬성이 불가능해지면 부결, 7일이 지나면 종료.
 * - 당선되면 4간부나 당선자가 임명일·임기를 정해 임명한다.
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
    private final OfficerTermService termService;

    @Transactional
    public List<ElectionResponse> list(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        closeExpired(presidiumId);

        List<OfficerElection> all = electionRepository.findByPresidiumIdWithUsers(presidiumId);
        List<OfficerElection> visible = new ArrayList<>(
                all.stream().filter(e -> e.isOpen() || e.isElected()).toList());
        all.stream().filter(e -> !e.isOpen() && !e.isElected()).limit(CLOSED_HISTORY_SIZE).forEach(visible::add);
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
        if (!candidate.getGrade().isActiveMember()) {
            throw GovernanceException.badRequest("간부는 행동단원(쁘레또리움 단원 포함)만 맡을 수 있습니다.");
        }
        if (candidate.getOfficerRole() == position) {
            throw GovernanceException.conflict("이미 " + position.getLabel() + " 직책을 맡고 있는 단원입니다.");
        }
        if (electionRepository.existsByPresidiumIdAndPositionAndStatus(presidiumId, position, ElectionStatus.OPEN)) {
            throw GovernanceException.conflict(position.getLabel() + " 투표가 이미 진행 중입니다.");
        }
        if (electionRepository.existsByPresidiumIdAndPositionAndStatus(presidiumId, position, ElectionStatus.ELECTED)) {
            throw GovernanceException.conflict(position.getLabel() + " 당선자의 임명을 먼저 마쳐 주세요.");
        }

        List<Long> voterIds = membershipRepository.findByPresidiumIdWithUser(presidiumId).stream()
                .filter(m -> m.getGrade().isActiveMember())
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
                NotificationService.presidiumTag(me.getPresidium().getName()) + "간부 이동 투표",
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

    /**
     * 당선된 후보를 임명한다. 4간부나 당선자 본인이 임명일(초기 설정을 위해 지난 날짜도 가능)과 임기를 정한다.
     * 같은 직책의 이전 간부는 물러난다.
     */
    @Transactional
    public ElectionResponse appoint(Long userId, Long electionId, OfficerTermRequest request) {
        PresidiumMembership me = membershipLookup.require(userId);
        OfficerElection election = findInPresidium(electionId, me);
        if (!election.isElected()) {
            throw GovernanceException.conflict("당선된 투표만 임명할 수 있습니다.");
        }
        if (!canAppoint(election, me)) {
            throw GovernanceException.forbidden("4간부나 당선자 본인만 임명할 수 있습니다.");
        }
        LocalDate appointedDate = OfficerTermService.parseAppointedDate(request.appointedDate());
        int termMonths = OfficerTermService.termMonths(request);

        Long presidiumId = election.getPresidium().getId();
        PresidiumMembership candidate = membershipRepository
                .findByPresidiumIdAndUserId(presidiumId, election.getCandidate().getId())
                .filter(m -> m.getGrade().isActiveMember())
                .orElse(null);
        if (candidate == null) {
            finish(election, ElectionStatus.CANCELED);
            throw GovernanceException.conflict("당선자가 더 이상 행동단원이 아니어서 투표가 취소되었습니다.");
        }

        membershipRepository.findByPresidiumIdAndOfficerRole(presidiumId, election.getPosition())
                .filter(previous -> !previous.getId().equals(candidate.getId()))
                .ifPresent(PresidiumMembership::dismissOfficer);
        candidate.appoint(election.getPosition(), appointedDate, termMonths);
        election.markAppointed(appointedDate, termMonths);
        notifyResult(election, "%s 단원이 %s %s 임명되었습니다. (임기 %s)".formatted(
                displayName(election.getCandidate()),
                "%d년 %d월 %d일".formatted(appointedDate.getYear(), appointedDate.getMonthValue(), appointedDate.getDayOfMonth()),
                withEuro(election.getPosition().getLabel()),
                GovernanceDtos.termLabel(termMonths)));
        termService.notifyIfEnding(candidate, OfficerTermService.today());
        return toResponse(election, me);
    }

    /** 당선 뒤 임명하지 않기로 하면 4간부가 취소한다 (당선자가 고사한 경우 등). */
    @Transactional
    public ElectionResponse cancelElected(Long userId, Long electionId) {
        PresidiumMembership me = membershipLookup.require(userId);
        OfficerElection election = findInPresidium(electionId, me);
        if (!election.isElected()) {
            throw GovernanceException.conflict("임명을 기다리는 당선만 취소할 수 있습니다.");
        }
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("4간부만 취소할 수 있습니다.");
        }
        finish(election, ElectionStatus.CANCELED);
        return toResponse(election, me);
    }

    /** 퇴단 등으로 후보가 사라진 진행 중·임명 대기 투표를 취소한다. */
    @Transactional
    public void cancelOpenElectionsForCandidate(Long presidiumId, Long candidateUserId) {
        for (ElectionStatus status : List.of(ElectionStatus.OPEN, ElectionStatus.ELECTED)) {
            for (OfficerElection election : electionRepository.findByPresidiumIdAndCandidateIdAndStatus(
                    presidiumId, candidateUserId, status)) {
                finish(election, ElectionStatus.CANCELED);
            }
        }
    }

    private OfficerElection findInPresidium(Long electionId, PresidiumMembership me) {
        return electionRepository.findById(electionId)
                .filter(e -> e.getPresidium().getId().equals(me.getPresidium().getId()))
                .orElseThrow(() -> GovernanceException.notFound("투표를 찾을 수 없습니다."));
    }

    private static boolean canAppoint(OfficerElection election, PresidiumMembership me) {
        return election.isElected()
                && (me.isOfficer() || election.getCandidate().getId().equals(me.getUser().getId()));
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
            elect(election);
        } else if (election.getEligibleVoterCount() - no < required) {
            finish(election, ElectionStatus.REJECTED);
        }
    }

    /** 과반 찬성: 당선. 임명일·임기를 정해 임명할 때까지 간부가 되지는 않는다. */
    private void elect(OfficerElection election) {
        boolean stillActive = membershipRepository
                .findByPresidiumIdAndUserId(election.getPresidium().getId(), election.getCandidate().getId())
                .filter(m -> m.getGrade().isActiveMember())
                .isPresent();
        finish(election, stillActive ? ElectionStatus.ELECTED : ElectionStatus.CANCELED);
    }

    private void finish(OfficerElection election, ElectionStatus result) {
        election.close(result);

        String candidateName = displayName(election.getCandidate());
        String position = election.getPosition().getLabel();
        String message = switch (result) {
            case ELECTED -> "%s 단원이 %s 당선되었습니다. 간부 이동 화면에서 임명일과 임기를 정해 임명해 주세요."
                    .formatted(candidateName, withEuro(position));
            case REJECTED -> "%s 단원의 %s 임명 투표가 부결되었습니다.".formatted(candidateName, position);
            case EXPIRED -> "%s 단원의 %s 임명 투표가 기한 내에 결정되지 않아 종료되었습니다.".formatted(candidateName, position);
            case CANCELED -> "%s 단원의 %s 임명 투표가 취소되었습니다.".formatted(candidateName, position);
            case OPEN, APPOINTED -> throw new IllegalArgumentException(result + " is not a closing result");
        };
        notifyResult(election, message);
    }

    private void notifyResult(OfficerElection election, String message) {
        List<Long> memberIds = membershipRepository.findByPresidiumIdWithUser(election.getPresidium().getId()).stream()
                .map(m -> m.getUser().getId())
                .toList();
        notificationService.notify(memberIds, NotificationType.ELECTION_RESULT,
                NotificationService.presidiumTag(election.getPresidium().getName()) + "간부 이동 투표 결과",
                message, election.getId());
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
        if (!me.getGrade().isActiveMember()) {
            return "행동단원(쁘레또리움 단원 포함)만 투표할 수 있습니다.";
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
                canAppoint(election, me),
                election.getAppointedDate(),
                GovernanceDtos.termLabel(election.getTermMonths()),
                toInstant(election.getCreatedAt()),
                toInstant(election.getClosesAt()),
                toInstant(election.getClosedAt()));
    }
}
