package com.angelasoft.presidium;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresidiumMembershipRepository extends JpaRepository<PresidiumMembership, Long> {

    /** 내가 가입한 쁘레시디움 전체. 어른 쁘레시디움이 먼저, 그다음 가입한 순서. */
    @Query("""
            SELECT m FROM PresidiumMembership m
            JOIN FETCH m.presidium p
            JOIN FETCH p.curia c
            JOIN FETCH c.parish
            WHERE m.user.id = :userId
            ORDER BY CASE WHEN p.type = com.angelasoft.presidium.PresidiumType.JUNIOR THEN 1 ELSE 0 END,
                     m.joinedAt ASC, m.id ASC
            """)
    List<PresidiumMembership> findAllByUserIdWithDetails(@Param("userId") Long userId);

    @Query("""
            SELECT m FROM PresidiumMembership m
            JOIN FETCH m.presidium p
            JOIN FETCH p.curia c
            JOIN FETCH c.parish
            WHERE m.user.id = :userId AND p.id = :presidiumId
            """)
    Optional<PresidiumMembership> findByUserIdAndPresidiumIdWithDetails(
            @Param("userId") Long userId, @Param("presidiumId") Long presidiumId);

    @Query("""
            SELECT m FROM PresidiumMembership m
            JOIN FETCH m.user
            WHERE m.presidium.id = :presidiumId
            ORDER BY m.joinedAt ASC
            """)
    List<PresidiumMembership> findByPresidiumIdWithUser(@Param("presidiumId") Long presidiumId);

    Optional<PresidiumMembership> findByPresidiumIdAndUserId(Long presidiumId, Long userId);

    Optional<PresidiumMembership> findByPresidiumIdAndOfficerRole(Long presidiumId, OfficerRole officerRole);

    List<PresidiumMembership> findByPresidiumIdAndOfficerRoleIsNotNull(Long presidiumId);

    /** 임명일·임기가 있고 이번 임기 만료 알림을 아직 보내지 않은 간부. */
    @Query("""
            SELECT m FROM PresidiumMembership m
            JOIN FETCH m.presidium
            JOIN FETCH m.user
            WHERE m.officerRole IS NOT NULL
              AND m.officerAppointedDate IS NOT NULL
              AND m.officerTermMonths IS NOT NULL
              AND (m.officerTermNoticeSent IS NULL OR m.officerTermNoticeSent = false)
            """)
    List<PresidiumMembership> findOfficersAwaitingTermNotice();
}
