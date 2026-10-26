package com.angelasoft.presidium.governance;

import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 로그인한 사용자의 가입 정보를 찾는다. 가입하지 않았으면 404. */
@Component
@RequiredArgsConstructor
class MembershipLookup {

    private final PresidiumMembershipRepository membershipRepository;

    PresidiumMembership require(Long userId) {
        return membershipRepository.findByUserId(userId)
                .orElseThrow(() -> GovernanceException.notFound("가입한 쁘레시디움이 없습니다."));
    }
}
