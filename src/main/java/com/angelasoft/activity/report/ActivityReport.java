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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단원이 한 번 제출한 활동보고. 통계는 제출한 날짜(한국 날짜, reportDate) 기준으로 묶는다.
 * 퇴단해도 쁘레시디움 통계에 남도록 가입 정보가 아니라 사용자와 쁘레시디움을 직접 참조한다.
 */
@Entity
@Table(
        name = "activity_reports",
        indexes = @Index(name = "idx_activity_reports_presidium_date", columnList = "presidium_id, report_date")
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

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    @Builder
    private ActivityReport(Presidium presidium, User member, LocalDate reportDate) {
        this.presidium = presidium;
        this.member = member;
        this.reportDate = reportDate;
        this.submittedAt = LocalDateTime.now();
    }
}
