package com.angelasoft.activity.report;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityReportRepository extends JpaRepository<ActivityReport, Long> {

    @Query("""
            SELECT r FROM ActivityReport r
            WHERE r.presidium.id = :presidiumId AND r.member.id = :memberId AND r.weekStart = :weekStart
            """)
    Optional<ActivityReport> findWeekly(
            @Param("presidiumId") Long presidiumId, @Param("memberId") Long memberId,
            @Param("weekStart") LocalDate weekStart);

    @Query("""
            SELECT r FROM ActivityReport r JOIN FETCH r.presidium JOIN FETCH r.member
            WHERE r.weekStart IS NULL
            ORDER BY r.reportDate ASC, r.id ASC
            """)
    List<ActivityReport> findWithoutWeekStart();
}
