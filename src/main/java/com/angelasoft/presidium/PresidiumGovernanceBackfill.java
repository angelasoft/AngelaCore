package com.angelasoft.presidium;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 간부·등급 기능 도입 전에 만들어진 쁘레시디움 데이터를 맞춘다.
 *
 * - 등급이 비어 있는 가입 정보 → 활동단원
 * - 만든 사람이 기록되지 않은 쁘레시디움 → 가장 먼저 가입한 단원을 만든 사람으로 보고 단장으로 임명 (단장이 없을 때만)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresidiumGovernanceBackfill implements ApplicationRunner {

    private final PresidiumRepository presidiumRepository;
    private final PresidiumMembershipRepository membershipRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int gradeFilled = 0;
        for (PresidiumMembership m : membershipRepository.findAll()) {
            if (m.getRawGrade() == null) {
                m.changeGrade(MemberGrade.ACTIVE);
                gradeFilled++;
            }
        }

        int presidentsAssigned = 0;
        for (Presidium presidium : presidiumRepository.findByCreatedByIsNull()) {
            List<PresidiumMembership> members = membershipRepository.findByPresidiumIdWithUser(presidium.getId());
            if (members.isEmpty()) {
                continue;
            }
            PresidiumMembership founder = members.get(0);
            presidium.assignCreator(founder.getUser());

            boolean hasPresident = members.stream().anyMatch(m -> m.getOfficerRole() == OfficerRole.PRESIDENT);
            if (!hasPresident && founder.getOfficerRole() == null) {
                founder.appoint(OfficerRole.PRESIDENT);
                presidentsAssigned++;
            }
        }

        if (gradeFilled > 0 || presidentsAssigned > 0) {
            log.info("[GovernanceBackfill] 등급 채움 {}건, 단장 지정 {}건", gradeFilled, presidentsAssigned);
        }
    }
}
