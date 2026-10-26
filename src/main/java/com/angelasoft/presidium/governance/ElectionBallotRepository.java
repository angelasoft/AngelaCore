package com.angelasoft.presidium.governance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionBallotRepository extends JpaRepository<ElectionBallot, Long> {

    List<ElectionBallot> findByElectionId(Long electionId);

    boolean existsByElectionIdAndVoterId(Long electionId, Long voterUserId);
}
