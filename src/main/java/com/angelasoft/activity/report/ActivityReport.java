package com.angelasoft.activity.report;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.Presidium;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단원의 한 주(월요일~일요일) 활동보고. 단원·쁘레시디움·주마다 한 건만 있고, 같은 주에 다시 제출하면 내용을 덮어쓴다.
 * 통계는 그 주에 처음 제출한 날짜(한국 날짜, reportDate) 기준으로 묶는다.
 * 퇴단해도 쁘레시디움 통계에 남도록 가입 정보가 아니라 사용자와 쁘레시디움을 직접 참조한다.
 */
@Entity
@Table(
        name = "activity_reports",
        indexes = @Index(name = "idx_activity_reports_presidium_date", columnList = "presidium_id, report_date"),
        uniqueConstraints = @UniqueConstraint(
                name = "uk_activity_reports_member_week",
                columnNames = {"presidium_id", "member_user_id", "week_start"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_user_id", nullable = false)
    private User member;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    /** 그 주의 월요일. 주차 도입 전 데이터가 있어 DB 에서는 null 을 허용하고 기동 시 채운다. */
    @Column(name = "week_start")
    private LocalDate weekStart;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime updatedAt;

    @Builder
    private ActivityReport(Presidium presidium, User member, LocalDate reportDate, LocalDate weekStart) {
        this.presidium = presidium;
        this.member = member;
        this.reportDate = reportDate;
        this.weekStart = weekStart;
        this.submittedAt = LocalDateTime.now();
        this.updatedAt = this.submittedAt;
    }

    public void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }

    public void assignWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt != null ? updatedAt : submittedAt;
    }
}
