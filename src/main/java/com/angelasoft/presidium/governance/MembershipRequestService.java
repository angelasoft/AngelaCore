package com.angelasoft.presidium.governance;

import static com.angelasoft.presidium.governance.GovernanceDtos.displayName;
import static com.angelasoft.presidium.governance.GovernanceDtos.toInstant;

import com.angelasoft.activity.Activity;
import com.angelasoft.activity.ActivityRegistry;
import com.angelasoft.notification.NotificationService;
import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestResponse;
import com.angelasoft.presidium.governance.GovernanceDtos.RequestsResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 협조단원·활동단원·퇴단 신청과 활동 등록 요청.
 *
 * - 등급 변경·퇴단: 간부는 간부 이동으로 후임이 임명된 뒤 신청할 수 있다. 처리 중인 신청은 한 사람당 하나.
 * - 활동 등록 요청: 간부를 포함해 누구나 할 수 있고, 승인되면 모든 쁘레시디움의 활동 목록에 추가된다.
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
    private final ActivityRegistry activityRegistry;

    private static final Set<MembershipRequestType> MEMBERSHIP_CHANGE_TYPES = Arrays.stream(MembershipRequestType.values())
            .filter(MembershipRequestType::changesMembership)
            .collect(Collectors.toUnmodifiableSet());

    @Transactional(readOnly = true)
    public RequestsResponse list(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        List<RequestResponse> pending = me.isOfficer()
                ? requestRepository.findByPresidiumIdAndStatusWithRequester(
                                me.getPresidium().getId(), MembershipRequestStatus.PENDING).stream()
                        .map(r -> toResponse(r, me))
                        .toList()
                : List.of();
        List<RequestResponse> mine = requestRepository
                .findTop10ByRequesterIdAndPresidiumIdOrderByCreatedAtDesc(userId, me.getPresidium().getId()).stream()
                .map(r -> toResponse(r, me))
                .toList();
        return new RequestsResponse(me.isOfficer(), pending, mine);
    }

    @Transactional
    public RequestResponse submit(Long userId, MembershipRequestType type, String activityName) {
        if (type == null) {
            throw GovernanceException.badRequest("신청 종류를 선택해 주세요.");
        }
        PresidiumMembership me = membershipLookup.require(userId);
        Long presidiumId = me.getPresidium().getId();
        String activity = null;
        if (type.changesMembership()) {
            checkMembershipChange(me, type);
        } else {
            activity = checkActivityProposal(presidiumId, activityName);
        }

        List<PresidiumMembership> officers = membershipRepository.findByPresidiumIdAndOfficerRoleIsNotNull(presidiumId);
        if (officers.isEmpty()) {
            throw GovernanceException.conflict("승인할 간부가 없습니다. 먼저 간부 이동으로 간부를 임명해 주세요.");
        }

        MembershipRequest request = requestRepository.save(MembershipRequest.builder()
                .presidium(me.getPresidium())
                .requester(me.getUser())
                .type(type)
                .officerCount(officers.size())
                .activityName(activity)
                .build());

        String message = activity == null
                ? "%s 단원이 %s을 했습니다. 승인 여부를 결정해 주세요.".formatted(displayName(me.getUser()), type.getLabel())
                : "%s 단원이 '%s' 활동 등록을 요청했습니다. 승인하면 모든 쁘레시디움 활동 목록에 추가됩니다."
                        .formatted(displayName(me.getUser()), activity);
        notificationService.notify(
                officers.stream().map(o -> o.getUser().getId()).toList(),
                NotificationType.MEMBERSHIP_REQUEST,
                NotificationService.presidiumTag(me.getPresidium().getName()) + type.getLabel() + " 승인 요청",
                message,
                request.getId());

        return toResponse(request, me);
    }

    /** 등급 변경·퇴단: 간부는 후임 임명 뒤에만, 처리 중인 등급 변경·퇴단 신청은 한 번에 하나. */
    private void checkMembershipChange(PresidiumMembership me, MembershipRequestType type) {
        if (me.isOfficer()) {
            throw GovernanceException.badRequest(
                    "간부는 간부 이동으로 후임이 임명된 뒤 " + type.getLabel() + "을 할 수 있습니다.");
        }
        if (type == MembershipRequestType.AUXILIARY && me.getGrade() == MemberGrade.AUXILIARY) {
            throw GovernanceException.conflict("이미 협조단원입니다.");
        }
        if (type == MembershipRequestType.ACTIVE && me.getGrade() == MemberGrade.ACTIVE) {
            throw GovernanceException.conflict("이미 활동단원입니다.");
        }
        if (requestRepository.existsByRequesterIdAndPresidiumIdAndStatusAndTypeIn(
                me.getUser().getId(), me.getPresidium().getId(), MembershipRequestStatus.PENDING,
                MEMBERSHIP_CHANGE_TYPES)) {
            throw GovernanceException.conflict("처리 중인 신청이 있습니다. 승인 결과를 기다려 주세요.");
        }
    }

    /** 활동 등록 요청: 간부도 할 수 있고, 이미 목록에 있거나 같은 요청이 처리 중이면 막는다. 다듬은 이름을 돌려준다. */
    private String checkActivityProposal(Long presidiumId, String activityName) {
        String name = activityName == null ? "" : activityName.strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw GovernanceException.badRequest("등록할 활동 이름을 입력해 주세요.");
        }
        if (name.length() > ActivityRegistry.MAX_NAME_LENGTH) {
            throw GovernanceException.badRequest(
                    "활동 이름은 " + ActivityRegistry.MAX_NAME_LENGTH + "자까지 입력할 수 있습니다.");
        }
        if (activityRegistry.findActive(name).isPresent()) {
            throw GovernanceException.conflict("'" + name + "'은 이미 활동 목록에 있습니다.");
        }
        String key = Activity.normalize(name);
        boolean pending = requestRepository
                .findByPresidiumIdAndStatusWithRequester(presidiumId, MembershipRequestStatus.PENDING).stream()
                .anyMatch(r -> r.getType() == MembershipRequestType.ACTIVITY
                        && Activity.normalize(r.getActivityName()).equals(key));
        if (pending) {
            throw GovernanceException.conflict("'" + name + "' 활동 등록 요청이 이미 승인을 기다리고 있습니다.");
        }
        return name;
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
        if (request.getType() == MembershipRequestType.ACTIVITY) {
            activityRegistry.addCustom(request.getActivityName());
            finish(request, MembershipRequestStatus.APPROVED);
            return;
        }
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
            case ACTIVE -> requester.changeGrade(MemberGrade.ACTIVE);
            case WITHDRAWAL -> {
                electionService.cancelOpenElectionsForCandidate(request.getPresidium().getId(), requesterId);
                membershipRepository.delete(requester);
            }
        }
        finish(request, MembershipRequestStatus.APPROVED);
    }

    private void finish(MembershipRequest request, MembershipRequestStatus result) {
        request.finish(result);
        String label = request.getActivityName() == null
                ? request.getType().getLabel()
                : "'" + request.getActivityName() + "' " + request.getType().getLabel();
        String message = switch (result) {
            case APPROVED -> switch (request.getType()) {
                case WITHDRAWAL -> "퇴단 신청이 승인되어 쁘레시디움에서 퇴단 처리되었습니다.";
                case AUXILIARY -> "협조단원 신청이 승인되어 협조단원으로 변경되었습니다.";
                case ACTIVE -> "활동단원 신청이 승인되어 활동단원으로 변경되었습니다.";
                case ACTIVITY -> "'%s' 활동이 등록되어 모든 쁘레시디움 활동보고 목록에 추가되었습니다."
                        .formatted(request.getActivityName());
            };
            case REJECTED -> label + "이 반려되었습니다.";
            case CANCELED -> label + "이 취소되었습니다.";
            case PENDING -> throw new IllegalArgumentException("PENDING is not a result");
        };
        notificationService.notify(
                List.of(request.getRequester().getId()),
                NotificationType.MEMBERSHIP_REQUEST_RESULT,
                NotificationService.presidiumTag(request.getPresidium().getName()) + label + " 결과",
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
                request.getActivityName(),
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
