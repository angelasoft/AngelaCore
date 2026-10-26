package com.angelasoft.presidium.governance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.PresidiumMembership;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** 간부 투표·단원 신청 API 요청/응답. 시각은 앱에서 현지 시간으로 바꿀 수 있게 UTC(Instant)로 내려준다. */
public final class GovernanceDtos {

    private GovernanceDtos() {
    }

    /** 단원 표시 이름. 예: "박우석(유스티노)". */
    static String displayName(User user) {
        String name = user.getName() == null || user.getName().isBlank() ? user.getEmail() : user.getName();
        String baptismal = user.getBaptismalName();
        return baptismal == null || baptismal.isBlank() ? name : name + "(" + baptismal + ")";
    }

    static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant();
    }

    public record MemberResponse(
            Long userId,
            String displayName,
            MemberGrade grade,
            String gradeLabel,
            OfficerRole officerRole,
            String officerLabel,
            Instant joinedAt,
            boolean me
    ) {
        static MemberResponse of(PresidiumMembership m, Long currentUserId) {
            OfficerRole role = m.getOfficerRole();
            return new MemberResponse(
                    m.getUser().getId(),
                    GovernanceDtos.displayName(m.getUser()),
                    m.getGrade(),
                    m.getGrade().getLabel(),
                    role,
                    role == null ? null : role.getLabel(),
                    toInstant(m.getJoinedAt()),
                    m.getUser().getId().equals(currentUserId));
        }
    }

    public record StartElectionRequest(OfficerRole position, Long candidateUserId) {
    }

    public record VoteRequest(Boolean approve) {
    }

    public record ElectionResponse(
            Long id,
            OfficerRole position,
            String positionLabel,
            Long candidateUserId,
            String candidateName,
            String startedByName,
            ElectionStatus status,
            int eligibleVoterCount,
            int requiredVotes,
            long yesCount,
            long noCount,
            Boolean myVote,
            boolean canVote,
            Instant createdAt,
            Instant closesAt,
            Instant closedAt
    ) {
    }

    public record SubmitRequest(MembershipRequestType type) {
    }

    public record DecisionRequest(Boolean approve) {
    }

    public record RequestResponse(
            Long id,
            MembershipRequestType type,
            String typeLabel,
            Long requesterUserId,
            String requesterName,
            MembershipRequestStatus status,
            int officerCount,
            int requiredApprovals,
            long approveCount,
            long rejectCount,
            Boolean myDecision,
            boolean canDecide,
            Instant createdAt,
            Instant decidedAt
    ) {
    }

    /** 간부에게는 승인 대기 목록, 모두에게는 내 신청 내역. */
    public record RequestsResponse(
            boolean officer,
            List<RequestResponse> pendingApprovals,
            List<RequestResponse> myRequests
    ) {
    }
}
