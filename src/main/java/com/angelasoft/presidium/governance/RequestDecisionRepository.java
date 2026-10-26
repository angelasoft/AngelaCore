package com.angelasoft.presidium.governance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestDecisionRepository extends JpaRepository<RequestDecision, Long> {

    List<RequestDecision> findByRequestId(Long requestId);

    boolean existsByRequestIdAndOfficerId(Long requestId, Long officerUserId);
}
