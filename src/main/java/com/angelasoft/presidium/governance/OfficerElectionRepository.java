package com.angelasoft.presidium.governance;

import com.angelasoft.presidium.OfficerRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfficerElectionRepository extends JpaRepository<OfficerElection, Long> {

    @Query("""
            SELECT e FROM OfficerElection e
            JOIN FETCH e.candidate
            JOIN FETCH e.startedBy
            WHERE e.presidium.id = :presidiumId
            ORDER BY e.createdAt DESC
            """)
    List<OfficerElection> findByPresidiumIdWithUsers(@Param("presidiumId") Long presidiumId);

    List<OfficerElection> findByPresidiumIdAndStatus(Long presidiumId, ElectionStatus status);

    boolean existsByPresidiumIdAndPositionAndStatus(Long presidiumId, OfficerRole position, ElectionStatus status);

    List<OfficerElection> findByCandidateIdAndStatus(Long candidateUserId, ElectionStatus status);
}
