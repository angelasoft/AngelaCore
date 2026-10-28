package com.angelasoft.activity.report;

import com.angelasoft.activity.Activity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 활동보고 한 건 안의 활동별 횟수. */
@Entity
@Table(name = "activity_report_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private ActivityReport report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(nullable = false)
    private int quantity;

    @Builder
    private ActivityReportItem(ActivityReport report, Activity activity, int quantity) {
        this.report = report;
        this.activity = activity;
        this.quantity = quantity;
    }
}
