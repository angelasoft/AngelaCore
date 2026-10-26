package com.angelasoft.presidium.governance;

import static com.angelasoft.presidium.governance.GovernanceDtos.displayName;
import static com.angelasoft.presidium.governance.GovernanceDtos.toInstant;

import com.angelasoft.notification.NotificationService;
import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협조단원 신청·퇴단 신청.
 *
 * - 간부는 간부 이동으로 후임이 임명된 뒤 신청할 수 있다. 처리 중인 신청은 한 사람당 하나.
 * - 신청하면 임명된 4간부 각각에게 알림이 가고, 신청 시점 간부 수의 과반이 승인하면 처리된다.
 */
@Service
@RequiredArgsConstructor
public class MembershipRequestService {

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final MembershipRequestRepository requestRepository;
    private final RequestDecisionRepository decisionRepository;
    private final OfficerElectionService electionService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public RequestsResponse list(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        List<RequestResponse> pending = me.isOfficer()
                ? requestRepository.findByPresidiumIdAndStatusWithRequester(
                                me.getPresidium().getId(), MembershipRequestStatus.PENDING).stream()
                        .map(r -> toResponse(r, me))
                        .toList()
                : List.of();
        List<RequestResponse> mine = requestRepository.findTop10ByRequesterIdOrderByCreatedAtDesc(userId).stream()
                .map(r -> toResponse(r, me))
                .toList();
        return new RequestsResponse(me.isOfficer(), pending, mine);
    }

    @Transactional
    public RequestResponse submit(Long userId, MembershipRequestType type) {
        if (type == null) {
            throw GovernanceException.badRequest("신청 종류를 선택해 주세요.");
        }
        PresidiumMembership me = membershipLookup.require(userId);
        if (me.isOfficer()) {
            throw GovernanceException.badRequest(
                    "간부는 간부 이동으로 후임이 임명된 뒤 " + type.getLabel() + "을 할 수 있습니다.");
        }
        if (type == MembershipRequestType.AUXILIARY && me.getGrade() == MemberGrade.AUXILIARY) {
            throw GovernanceException.conflict("이미 협조단원입니다.");
        }
        if (requestRepository.existsByRequesterIdAndStatus(userId, MembershipRequestStatus.PENDING)) {
            throw GovernanceException.conflict("처리 중인 신청이 있습니다. 승인 결과를 기다려 주세요.");
        }

        List<PresidiumMembership> officers =
                membershipRepository.findByPresidiumIdAndOfficerRoleIsNotNull(me.getPresidium().getId());
        if (officers.isEmpty()) {
            throw GovernanceException.conflict("승인할 간부가 없습니다. 먼저 간부 이동으로 간부를 임명해 주세요.");
        }

        MembershipRequest request = requestRepository.save(MembershipRequest.builder()
                .presidium(me.getPresidium())
                .requester(me.getUser())
                .type(type)
                .officerCount(officers.size())
                .build());

        notificationService.notify(
                officers.stream().map(o -> o.getUser().getId()).toList(),
                NotificationType.MEMBERSHIP_REQUEST,
                type.getLabel() + " 승인 요청",
                "%s 단원이 %s을 했습니다. 승인 여부를 결정해 주세요.".formatted(displayName(me.getUser()), type.getLabel()),
                request.getId());

        return toResponse(request, me);
    }

    @Transactional
    public RequestResponse decide(Long userId, Long requestId, Boolean approve) {
        if (approve == null) {
            throw GovernanceException.badRequest("승인 또는 반려를 선택해 주세요.");
        }
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("4간부만 승인할 수 있습니다.");
        }
        MembershipRequest request = findInMyPresidium(requestId, me);
        if (!request.isPending()) {
            throw GovernanceException.conflict("이미 처리된 신청입니다.");
        }
        if (decisionRepository.existsByRequestIdAndOfficerId(requestId, userId)) {
            throw GovernanceException.conflict("이미 결정했습니다.");
        }

        decisionRepository.save(RequestDecision.builder()
                .request(request)
                .officer(me.getUser())
                .approve(approve)
                .build());

        List<RequestDecision> decisions = decisionRepository.findByRequestId(requestId);
        long approvals = decisions.stream().filter(RequestDecision::isApprove).count();
        long rejections = decisions.size() - approvals;
        int required = request.requiredApprovals();

        if (approvals >= required) {
            apply(request);
        } else if (request.getOfficerCount() - rejections < required) {
            finish(request, MembershipRequestStatus.REJECTED);
        }
        return toResponse(request, me);
    }

    @Transactional
    public RequestResponse cancel(Long userId, Long requestId) {
        PresidiumMembership me = membershipLookup.require(userId);
        MembershipRequest request = findInMyPresidium(requestId, me);
        if (!request.getRequester().getId().equals(userId)) {
            throw GovernanceException.forbidden("본인 신청만 취소할 수 있습니다.");
        }
        if (!request.isPending()) {
            throw GovernanceException.conflict("이미 처리된 신청입니다.");
        }
        request.finish(MembershipRequestStatus.CANCELED);
        return toResponse(request, me);
    }

    private MembershipRequest findInMyPresidium(Long requestId, PresidiumMembership me) {
        return requestRepository.findById(requestId)
                .filter(r -> r.getPresidium().getId().equals(me.getPresidium().getId()))
                .orElseThrow(() -> GovernanceException.notFound("신청을 찾을 수 없습니다."));
    }

    private void apply(MembershipRequest request) {
        Long requesterId = request.getRequester().getId();
        PresidiumMembership requester = membershipRepository
                .findByPresidiumIdAndUserId(request.getPresidium().getId(), requesterId)
                .orElse(null);
        if (requester == null) {
            finish(request, MembershipRequestStatus.CANCELED);
            return;
        }

        switch (request.getType()) {
            case AUXILIARY -> {
                requester.dismissOfficer();
                requester.changeGrade(MemberGrade.AUXILIARY);
            }
            case WITHDRAWAL -> {
                electionService.cancelOpenElectionsForCandidate(requesterId);
                membershipRepository.delete(requester);
            }
        }
        finish(request, MembershipRequestStatus.APPROVED);
    }

    private void finish(MembershipRequest request, MembershipRequestStatus result) {
        request.finish(result);
        String label = request.getType().getLabel();
        String message = switch (result) {
            case APPROVED -> request.getType() == MembershipRequestType.WITHDRAWAL
                    ? "퇴단 신청이 승인되어 쁘레시디움에서 퇴단 처리되었습니다."
                    : "협조단원 신청이 승인되어 협조단원으로 변경되었습니다.";
            case REJECTED -> label + "이 반려되었습니다.";
            case CANCELED -> label + "이 취소되었습니다.";
            case PENDING -> throw new IllegalArgumentException("PENDING is not a result");
        };
        notificationService.notify(
                List.of(request.getRequester().getId()),
                NotificationType.MEMBERSHIP_REQUEST_RESULT,
                label + " 결과",
                message,
                request.getId());
    }

    private RequestResponse toResponse(MembershipRequest request, PresidiumMembership me) {
        List<RequestDecision> decisions = decisionRepository.findByRequestId(request.getId());
        long approvals = decisions.stream().filter(RequestDecision::isApprove).count();
        Boolean myDecision = decisions.stream()
                .filter(d -> d.getOfficer().getId().equals(me.getUser().getId()))
                .map(RequestDecision::isApprove)
                .findFirst()
                .orElse(null);
        boolean canDecide = request.isPending() && me.isOfficer() && myDecision == null;

        return new RequestResponse(
                request.getId(),
                request.getType(),
                request.getType().getLabel(),
                request.getRequester().getId(),
                displayName(request.getRequester()),
                request.getStatus(),
                request.getOfficerCount(),
                request.requiredApprovals(),
                approvals,
                decisions.size() - approvals,
                myDecision,
                canDecide,
                toInstant(request.getCreatedAt()),
                toInstant(request.getDecidedAt()));
    }
}
