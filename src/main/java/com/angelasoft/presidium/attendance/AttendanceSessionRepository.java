package com.angelasoft.presidium.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    /** 최근 회합이 먼저. 기록과 단원까지 함께 읽는다. */
    @Query("""
            SELECT DISTINCT s FROM AttendanceSession s
            LEFT JOIN FETCH s.records r
            LEFT JOIN FETCH r.user
            WHERE s.presidium.id = :presidiumId AND s.kind = :kind
            ORDER BY s.meetingDate DESC
            """)
    List<AttendanceSession> findWithRecords(@Param("presidiumId") Long presidiumId, @Param("kind") AttendanceKind kind);
}
