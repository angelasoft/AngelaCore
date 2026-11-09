package com.angelasoft.presidium.governance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.PresidiumMembership;
import java.time.Instant;
import java.time.LocalDate;
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

    /** 임기 표시. 36개월이면 "3년 0개월". */
    static String termLabel(Integer months) {
        return months == null ? null : "%d년 %d개월".formatted(months / 12, months % 12);
    }

    /** 간부면 임명일·임기·임기 마지막 날(입력 전이면 null)도 내려준다. */
    public record MemberResponse(
            Long userId,
            String displayName,
            MemberGrade grade,
            String gradeLabel,
            OfficerRole officerRole,
            String officerLabel,
            LocalDate officerAppointedDate,
            Integer officerTermMonths,
            String officerTermLabel,
            LocalDate officerTermEndDate,
            Instant joinedAt,
            boolean me
    ) {
        static MemberResponse of(PresidiumMembership m, Long currentUserId) {
            OfficerRole role = m.getOfficerRole();
            boolean officer = role != null;
            return new MemberResponse(
                    m.getUser().getId(),
                    GovernanceDtos.displayName(m.getUser()),
                    m.getGrade(),
                    m.getGrade().getLabel(),
                    role,
                    officer ? role.getLabel() : null,
                    officer ? m.getOfficerAppointedDate() : null,
                    officer ? m.getOfficerTermMonths() : null,
                    officer ? termLabel(m.getOfficerTermMonths()) : null,
                    m.getOfficerTermEndDate(),
                    toInstant(m.getJoinedAt()),
                    m.getUser().getId().equals(currentUserId));
        }
    }

    /**
     * 간부 임명 또는 임기 입력. 임명일은 "2026-01-07" (오늘이나 지난 날짜), 임기는 년·개월 (3년이면 3년 0개월).
     */
    public record OfficerTermRequest(String appointedDate, Integer termYears, Integer termMonths) {
    }

    public record StartElectionRequest(OfficerRole position, Long candidateUserId) {
    }

    /** true 면 쁘레또리움·아듀또리움 단원으로 임명, false 면 해제. */
    public record HigherDegreeRequest(Boolean appoint) {
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
            /** 당선(ELECTED) 상태에서 4간부와 당선자. */
            boolean canAppoint,
            LocalDate appointedDate,
            String termLabel,
            Instant createdAt,
            Instant closesAt,
            Instant closedAt
    ) {
    }

    /** activityName 은 type 이 ACTIVITY 일 때만. */
    public record SubmitRequest(MembershipRequestType type, String activityName) {
    }

    public record DecisionRequest(Boolean approve) {
    }

    public record RequestResponse(
            Long id,
            MembershipRequestType type,
            String typeLabel,
            String activityName,
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
