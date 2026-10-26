package com.angelasoft.presidium.governance;

import com.angelasoft.admin.security.AdminUserDetails;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.DecisionRequest;
import com.angelasoft.presidium.governance.GovernanceDtos.ElectionResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.MemberResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 쁘레시디움의 단원·간부 이동 투표·협조단원/퇴단 신청 API (인증 필요).
 *
 *   GET  /app/presidiums/me/members
 *   GET  /app/presidiums/me/elections
 *   POST /app/presidiums/me/elections                   {position, candidateUserId}  4간부만
 *   POST /app/presidiums/me/elections/{id}/votes        {approve}
 *   GET  /app/presidiums/me/requests
 *   POST /app/presidiums/me/requests                    {type: AUXILIARY | WITHDRAWAL}
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

    @GetMapping("/members")
    @Transactional(readOnly = true)
    public List<MemberResponse> members(@AuthenticationPrincipal AdminUserDetails principal) {
        PresidiumMembership me = membershipLookup.require(principal.getUserId());
        return membershipRepository.findByPresidiumIdWithUser(me.getPresidium().getId()).stream()
                .map(m -> MemberResponse.of(m, principal.getUserId()))
                .toList();
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
        return requestService.submit(principal.getUserId(), request.type());
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
