package com.angelasoft.presidium;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PresidiumRecessRepository extends JpaRepository<PresidiumRecess, Long> {

    /** 최근 주가 먼저. */
    List<PresidiumRecess> findByPresidiumIdOrderByWeekStartDesc(Long presidiumId);

    boolean existsByPresidiumIdAndWeekStart(Long presidiumId, LocalDate weekStart);

    Optional<PresidiumRecess> findByIdAndPresidiumId(Long id, Long presidiumId);
}
