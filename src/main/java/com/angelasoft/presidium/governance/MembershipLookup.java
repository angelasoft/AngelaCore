package com.angelasoft.presidium.governance;

import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 로그인한 사용자가 지금 보고 있는 쁘레시디움의 가입 정보를 찾는다.
 *
 * 앱은 여러 쁘레시디움(어른 1곳 + 소년소녀 여러 곳)에 가입한 단원이 고른 쁘레시디움을
 * {@value #PRESIDIUM_HEADER} 헤더로 보낸다. 헤더가 없으면 어른 쁘레시디움, 없으면 가장 먼저 가입한 곳을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class MembershipLookup {

    public static final String PRESIDIUM_HEADER = "X-Presidium-Id";

    private final PresidiumMembershipRepository membershipRepository;

    /** 가입하지 않았으면 404, 고른 쁘레시디움의 단원이 아니면 403. */
    public PresidiumMembership require(Long userId) {
        return find(userId).orElseThrow(() -> GovernanceException.notFound("가입한 쁘레시디움이 없습니다."));
    }

    /** 가입하지 않았으면 빈 값. 고른 쁘레시디움의 단원이 아니면 403. */
    public Optional<PresidiumMembership> find(Long userId) {
        Long selected = selectedPresidiumId();
        if (selected != null) {
            return Optional.of(membershipRepository.findByUserIdAndPresidiumIdWithDetails(userId, selected)
                    .orElseThrow(() -> GovernanceException.forbidden("이 쁘레시디움의 단원이 아닙니다.")));
        }
        return membershipRepository.findAllByUserIdWithDetails(userId).stream().findFirst();
    }

    private static Long selectedPresidiumId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String value = request.getHeader(PRESIDIUM_HEADER);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw GovernanceException.badRequest(PRESIDIUM_HEADER + " 헤더는 쁘레시디움 번호여야 합니다.");
        }
    }
}
