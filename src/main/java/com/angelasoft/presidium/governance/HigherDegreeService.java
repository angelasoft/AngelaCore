package com.angelasoft.presidium.governance;

import static com.angelasoft.presidium.governance.GovernanceDtos.displayName;

import com.angelasoft.notification.NotificationService;
import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.MemberGrade;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.governance.GovernanceDtos.MemberResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쁘레또리움(행동단원)·아듀또리움(협조단원) 단원 임명과 해제. 단장만 할 수 있고, 당사자에게 알림이 간다.
 */
@Service
@RequiredArgsConstructor
public class HigherDegreeService {

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final NotificationService notificationService;

    @Transactional
    public MemberResponse change(Long userId, Long memberUserId, Boolean appoint) {
        if (appoint == null) {
            throw GovernanceException.badRequest("임명 또는 해제를 선택해 주세요.");
        }
        PresidiumMembership me = membershipLookup.require(userId);
        if (me.getOfficerRole() != OfficerRole.PRESIDENT) {
            throw GovernanceException.forbidden("쁘레또리움·아듀또리움 단원은 단장만 임명할 수 있습니다.");
        }
        PresidiumMembership member = membershipRepository
                .findByPresidiumIdAndUserId(me.getPresidium().getId(), memberUserId)
                .orElseThrow(() -> GovernanceException.notFound("이 쁘레시디움 단원이 아닙니다."));

        MemberGrade before = member.getGrade();
        if (before.isHigherDegree() == appoint) {
            throw GovernanceException.conflict(appoint
                    ? "이미 " + before.getLabel() + "입니다."
                    : before.getLabel() + "은 쁘레또리움·아듀또리움 단원이 아닙니다.");
        }
        MemberGrade after = before.withHigherDegree(appoint);
        member.changeGrade(after);

        String higher = before.withHigherDegree(true).getLabel();
        String message = appoint
                ? "단장이 %s 님을 %s으로 임명했습니다.".formatted(displayName(member.getUser()), higher)
                : "단장이 %s 님의 %s 등록을 해제했습니다. 지금 등급은 %s입니다."
                        .formatted(displayName(member.getUser()), higher, after.getLabel());
        notificationService.notify(
                List.of(memberUserId),
                NotificationType.GRADE_CHANGED,
                NotificationService.presidiumTag(me.getPresidium().getName()) + higher + (appoint ? " 임명" : " 해제"),
                message,
                null);
        return MemberResponse.of(member, userId);
    }
}
