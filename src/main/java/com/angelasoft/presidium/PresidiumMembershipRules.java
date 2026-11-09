package com.angelasoft.presidium;

import java.util.List;
import java.util.Optional;

/** 가입 규칙: 어른 쁘레시디움은 1곳만, 소년소녀 쁘레시디움은 여러 곳 가입할 수 있다. */
final class PresidiumMembershipRules {

    private PresidiumMembershipRules() {
    }

    /** 이 쁘레시디움에 새로 가입할 수 없으면 그 이유. 가입할 수 있으면 빈 값. */
    static Optional<String> joinBlockReason(List<PresidiumMembership> mine, PresidiumType joiningType) {
        if (joiningType == PresidiumType.ADULT) {
            return mine.stream()
                    .filter(m -> m.getPresidium().getType() == PresidiumType.ADULT)
                    .findFirst()
                    .map(m -> "어른 쁘레시디움은 한 곳만 가입할 수 있습니다. 지금 가입한 '"
                            + m.getPresidium().getName() + "'에서 퇴단 승인을 받은 뒤 가입해 주세요.");
        }
        return Optional.empty();
    }
}
