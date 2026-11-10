package com.angelasoft.presidium.governance;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.PresidiumDissolutionService;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.DecisionRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.ElectionResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.HigherDegreeRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.MemberResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.OfficerTermRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestsResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.StartElectionRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.SubmitRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.VoteRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 쁘레시디움의 단원·간부 이동 투표·협조단원/퇴단 신청 API (인증 필요).
 *
 *   GET  /app/presidiums/me/members
 *   POST /app/presidiums/me/members/{userId}/higher-degree  {appoint}  쁘레또리움·아듀또리움 임명/해제, 단장만
 *   GET  /app/presidiums/me/elections
 *   POST /app/presidiums/me/elections                   {position, candidateUserId}  4간부만
 *   POST /app/presidiums/me/elections/{id}/votes        {approve}
 *   POST /app/presidiums/me/elections/{id}/appoint      {appointedDate, termYears, termMonths}  당선 후 4간부·당선자
 *   POST /app/presidiums/me/elections/{id}/cancel       당선 후 임명하지 않기로 할 때, 4간부만
 *   PUT  /app/presidiums/me/officers/{userId}/term      {appointedDate, termYears, termMonths}  4간부만
 *   POST /app/presidiums/me/dissolve                    {confirmName}  혼자 남은 단원의 퇴단 = 쁘레시디움 해산
 *   GET  /app/presidiums/me/requests
 *   POST /app/presidiums/me/requests                    {type: AUXILIARY | ACTIVE | WITHDRAWAL | ACTIVITY, activityName}
 *   POST /app/presidiums/me/requests/{id}/decision      {approve}  4간부만
 *   POST /app/presidiums/me/requests/{id}/cancel
 */
@RestController
@RequestMapping("/app/presidiums/me")
@RequiredArgsConstructor
public class PresidiumGovernanceController {

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final OfficerElectionService electionService;
    private final MembershipRequestService requestService;
    private final HigherDegreeService higherDegreeService;
    private final OfficerTermService termService;
    private final PresidiumDissolutionService dissolutionService;

    @GetMapping("/members")
    @Transactional(readOnly = true)
    public List<MemberResponse> members(@AuthenticationPrincipal AdminUserDetails principal) {
        PresidiumMembership me = membershipLookup.require(principal.getUserId());
        return membershipRepository.findByPresidiumIdWithUser(me.getPresidium().getId()).stream()
                .map(m -> MemberResponse.of(m, principal.getUserId()))
                .toList();
    }

    @PostMapping("/members/{memberUserId}/higher-degree")
    public MemberResponse changeHigherDegree(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long memberUserId,
            @RequestBody HigherDegreeRequest request
    ) {
        return higherDegreeService.change(principal.getUserId(), memberUserId, request.appoint());
    }

    @GetMapping("/elections")
    public List<ElectionResponse> elections(@AuthenticationPrincipal AdminUserDetails principal) {
        return electionService.list(principal.getUserId());
    }

    @PostMapping("/elections")
    @ResponseStatus(HttpStatus.CREATED)
    public ElectionResponse startElection(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody StartElectionRequest request
    ) {
        return electionService.start(principal.getUserId(), request.position(), request.candidateUserId());
    }

    @PostMapping("/elections/{electionId}/votes")
    public ElectionResponse vote(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long electionId,
            @RequestBody VoteRequest request
    ) {
        return electionService.vote(principal.getUserId(), electionId, request.approve());
    }

    @PostMapping("/elections/{electionId}/appoint")
    public ElectionResponse appoint(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long electionId,
            @RequestBody OfficerTermRequest request
    ) {
        return electionService.appoint(principal.getUserId(), electionId, request);
    }

    @PostMapping("/elections/{electionId}/cancel")
    public ElectionResponse cancelElected(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long electionId
    ) {
        return electionService.cancelElected(principal.getUserId(), electionId);
    }

    @PutMapping("/officers/{officerUserId}/term")
    public MemberResponse updateOfficerTerm(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long officerUserId,
            @RequestBody OfficerTermRequest request
    ) {
        return termService.updateTerm(principal.getUserId(), officerUserId, request);
    }

    /** 혼자 남은 단원의 퇴단: 쁘레시디움과 관련 데이터를 모두 지운다. */
    @PostMapping("/dissolve")
    public void dissolve(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody DissolveRequest request
    ) {
        dissolutionService.dissolve(principal.getUserId(), request.confirmName());
    }

    public record DissolveRequest(String confirmName) {
    }

    @GetMapping("/requests")
    public RequestsResponse requests(@AuthenticationPrincipal AdminUserDetails principal) {
        return requestService.list(principal.getUserId());
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse submit(
            @AuthenticationPrincipal AdminUserDetails principal,
            @RequestBody SubmitRequest request
    ) {
        return requestService.submit(principal.getUserId(), request.type(), request.activityName());
    }

    @PostMapping("/requests/{requestId}/decision")
    public RequestResponse decide(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long requestId,
            @RequestBody DecisionRequest request
    ) {
        return requestService.decide(principal.getUserId(), requestId, request.approve());
    }

    @PostMapping("/requests/{requestId}/cancel")
    public RequestResponse cancel(
            @AuthenticationPrincipal AdminUserDetails principal,
            @PathVariable Long requestId
    ) {
        return requestService.cancel(principal.getUserId(), requestId);
    }
}
