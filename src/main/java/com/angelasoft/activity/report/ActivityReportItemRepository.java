package com.angelasoft.activity.report;

import com.angelasoft.activity.report.ActivityReportProjections.ActivityTotal;
import com.angelasoft.activity.report.ActivityReportProjections.DateTotal;
import com.angelasoft.activity.report.ActivityReportProjections.MemberTotal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 활동보고 항목 저장 및 집계. 단원 필터가 있는 쿼리와 없는 쿼리를 나눈 것은
 * "단원 ID 가 null 이면 전체"처럼 null 파라미터를 쓰면 PostgreSQL 이 타입을 추론하지 못하기 때문이다.
 */
public interface ActivityReportItemRepository extends JpaRepository<ActivityReportItem, Long> {

    @Query("""
            SELECT a.id AS activityId, a.name AS name, SUM(i.quantity) AS total
            FROM ActivityReportItem i JOIN i.activity a JOIN i.report r
            WHERE r.presidium.id = :presidiumId AND r.reportDate BETWEEN :from AND :to
            GROUP BY a.id, a.name
            ORDER BY SUM(i.quantity) DESC, a.name ASC
            """)
    List<ActivityTotal> sumByActivity(
            @Param("presidiumId") Long presidiumId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT a.id AS activityId, a.name AS name, SUM(i.quantity) AS total
            FROM ActivityReportItem i JOIN i.activity a JOIN i.report r
            WHERE r.presidium.id = :presidiumId AND r.member.id = :memberId
              AND r.reportDate BETWEEN :from AND :to
            GROUP BY a.id, a.name
            ORDER BY SUM(i.quantity) DESC, a.name ASC
            """)
    List<ActivityTotal> sumByActivityForMember(
            @Param("presidiumId") Long presidiumId, @Param("memberId") Long memberId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT m.id AS userId, m.name AS name, m.baptismalName AS baptismalName, SUM(i.quantity) AS total
            FROM ActivityReportItem i JOIN i.report r JOIN r.member m
            WHERE r.presidium.id = :presidiumId AND r.reportDate BETWEEN :from AND :to
            GROUP BY m.id, m.name, m.baptismalName
            ORDER BY SUM(i.quantity) DESC, m.name ASC
            """)
    List<MemberTotal> sumByMember(
            @Param("presidiumId") Long presidiumId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT r.reportDate AS reportDate, SUM(i.quantity) AS total
            FROM ActivityReportItem i JOIN i.report r
            WHERE r.presidium.id = :presidiumId AND r.reportDate BETWEEN :from AND :to
            GROUP BY r.reportDate
            """)
    List<DateTotal> sumByDate(
            @Param("presidiumId") Long presidiumId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT r.reportDate AS reportDate, SUM(i.quantity) AS total
            FROM ActivityReportItem i JOIN i.report r
            WHERE r.presidium.id = :presidiumId AND r.member.id = :memberId
              AND r.reportDate BETWEEN :from AND :to
            GROUP BY r.reportDate
            """)
    List<DateTotal> sumByDateForMember(
            @Param("presidiumId") Long presidiumId, @Param("memberId") Long memberId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);
}
