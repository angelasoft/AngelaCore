package com.angelasoft.presidium;

import com.angelasoft.notification.NotificationType;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쁘레시디움 해산. 혼자 남은 단원(보통 단장)이 퇴단하면 쁘레시디움과 관련 데이터를 모두 지운다:
 * 간부 투표·신청과 그 알림, 출석부, 휴회, 회계 장부, 활동보고, 공지, 봉사 일정, 가입 정보.
 * 다른 쁘레시디움이 없는 평의회(Cu)도 함께 지운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresidiumDissolutionService {

    private static final List<NotificationType> ELECTION_NOTIFICATIONS =
            List.of(NotificationType.ELECTION_STARTED, NotificationType.ELECTION_RESULT);
    private static final List<NotificationType> REQUEST_NOTIFICATIONS =
            List.of(NotificationType.MEMBERSHIP_REQUEST, NotificationType.MEMBERSHIP_REQUEST_RESULT);

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final EntityManager em;

    /** 실수로 지우지 않게 Pr 이름을 입력해야 한다 (띄어쓰기·끝의 "Pr" 은 상관없음). */
    @Transactional
    public void dissolve(Long userId, String confirmName) {
        PresidiumMembership me = membershipLookup.require(userId);
        Presidium presidium = me.getPresidium();
        if (membershipRepository.findByPresidiumIdWithUser(presidium.getId()).size() > 1) {
            throw GovernanceException.conflict("다른 단원이 남아 있으면 쁘레시디움을 해산할 수 없습니다.");
        }
        if (confirmName == null || confirmName.isBlank()
                || !normalizeName(confirmName).equals(normalizeName(presidium.getName()))) {
            throw GovernanceException.badRequest("확인을 위해 Pr 이름을 입력해 주세요. 예: " + presidium.getName());
        }
        Long presidiumId = presidium.getId();
        Long curiaId = presidium.getCuria().getId();
        String name = presidium.getName();

        em.flush();
        deleteAll(presidiumId, curiaId);
        em.clear();
        log.info("쁘레시디움 해산: {} (id={}) by user {}", name, presidiumId, userId);
    }

    /** 해산 확인용: 띄어쓰기는 보지 않고 끝의 "Pr" 은 떼서 비교한다. "즐거움의 샘", "즐거움의샘Pr" 모두 같다. */
    static String normalizeName(String name) {
        String compact = name.replaceAll("\\s+", "");
        return compact.toLowerCase().endsWith("pr") ? compact.substring(0, compact.length() - 2) : compact;
    }

    private void deleteAll(Long presidiumId, Long curiaId) {
        List<Long> electionIds = ids("SELECT e.id FROM OfficerElection e WHERE e.presidium.id = :id", presidiumId);
        List<Long> requestIds = ids("SELECT r.id FROM MembershipRequest r WHERE r.presidium.id = :id", presidiumId);
        if (!electionIds.isEmpty()) {
            update("DELETE FROM Notification n WHERE n.type IN :types AND n.targetId IN :ids",
                    "types", ELECTION_NOTIFICATIONS, "ids", electionIds);
        }
        if (!requestIds.isEmpty()) {
            update("DELETE FROM Notification n WHERE n.type IN :types AND n.targetId IN :ids",
                    "types", REQUEST_NOTIFICATIONS, "ids", requestIds);
        }
        update("DELETE FROM Notification n WHERE n.type = :type AND n.targetId = :id",
                "type", NotificationType.OFFICER_TERM_ENDING, "id", presidiumId);

        // 자식 테이블부터.
        delete("DELETE FROM ElectionBallot b WHERE b.election.id IN "
                + "(SELECT e.id FROM OfficerElection e WHERE e.presidium.id = :id)", presidiumId);
        delete("DELETE FROM OfficerElection e WHERE e.presidium.id = :id", presidiumId);
        delete("DELETE FROM RequestDecision d WHERE d.request.id IN "
                + "(SELECT r.id FROM MembershipRequest r WHERE r.presidium.id = :id)", presidiumId);
        delete("DELETE FROM MembershipRequest r WHERE r.presidium.id = :id", presidiumId);
        delete("DELETE FROM AttendanceRecord a WHERE a.session.id IN "
                + "(SELECT s.id FROM AttendanceSession s WHERE s.presidium.id = :id)", presidiumId);
        delete("DELETE FROM AttendanceSession s WHERE s.presidium.id = :id", presidiumId);
        delete("DELETE FROM PresidiumRecess r WHERE r.presidium.id = :id", presidiumId);
        delete("DELETE FROM AccountEntry a WHERE a.ledger.id IN "
                + "(SELECT l.id FROM AccountLedger l WHERE l.presidium.id = :id)", presidiumId);
        delete("DELETE FROM AccountLedger l WHERE l.presidium.id = :id", presidiumId);
        delete("DELETE FROM ActivityReportItem i WHERE i.report.id IN "
                + "(SELECT r.id FROM ActivityReport r WHERE r.presidium.id = :id)", presidiumId);
        delete("DELETE FROM ActivityReport r WHERE r.presidium.id = :id", presidiumId);
        delete("DELETE FROM Notice n WHERE n.presidium.id = :id", presidiumId);
        delete("DELETE FROM VolunteerSchedule v WHERE v.presidium.id = :id", presidiumId);
        delete("DELETE FROM PresidiumMembership m WHERE m.presidium.id = :id", presidiumId);
        delete("DELETE FROM Presidium p WHERE p.id = :id", presidiumId);

        em.createQuery("DELETE FROM Curia c WHERE c.id = :id "
                        + "AND NOT EXISTS (SELECT p.id FROM Presidium p WHERE p.curia.id = :id)")
                .setParameter("id", curiaId)
                .executeUpdate();
    }

    private List<Long> ids(String jpql, Long presidiumId) {
        return em.createQuery(jpql, Long.class).setParameter("id", presidiumId).getResultList();
    }

    private void delete(String jpql, Long presidiumId) {
        em.createQuery(jpql).setParameter("id", presidiumId).executeUpdate();
    }

    private void update(String jpql, String name1, Object value1, String name2, Object value2) {
        em.createQuery(jpql).setParameter(name1, value1).setParameter(name2, value2).executeUpdate();
    }
}
