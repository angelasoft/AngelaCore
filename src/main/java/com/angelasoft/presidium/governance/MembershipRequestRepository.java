package com.angelasoft.presidium.governance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MembershipRequestRepository extends JpaRepository<MembershipRequest, Long> {

    @Query("""
            SELECT r FROM MembershipRequest r
            JOIN FETCH r.requester
            WHERE r.presidium.id = :presidiumId AND r.status = :status
            ORDER BY r.createdAt ASC
            """)
    List<MembershipRequest> findByPresidiumIdAndStatusWithRequester(
            @Param("presidiumId") Long presidiumId,
            @Param("status") MembershipRequestStatus status);

    List<MembershipRequest> findTop10ByRequesterIdOrderByCreatedAtDesc(Long requesterUserId);

    boolean existsByRequesterIdAndStatus(Long requesterUserId, MembershipRequestStatus status);
}
