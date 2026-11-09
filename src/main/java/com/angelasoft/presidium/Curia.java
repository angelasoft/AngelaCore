package com.angelasoft.presidium;

import com.angelasoft.region.Parish;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 큐리아(Cu). 본당 소속 쁘레시디움 상위 단위. */
@Entity
@Table(
        name = "curias",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parish_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Curia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parish_id", nullable = false)
    private Parish parish;

    @Column(nullable = false, length = 100)
    private String name;

    /** 월례회의: 매달 몇째 주(1~4, 5는 마지막 주) 무슨 요일. 같은 Cu 의 모든 Pr 이 함께 쓴다. 입력 전에는 null. */
    private Integer councilWeekOfMonth;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DayOfWeek councilDay;

    private LocalTime councilTime;

    @Column(length = 100)
    private String councilPlace;

    @Builder
    private Curia(Parish parish, String name) {
        this.parish = parish;
        this.name = name;
    }

    public void updateCouncilSchedule(Integer weekOfMonth, DayOfWeek day, LocalTime time, String place) {
        this.councilWeekOfMonth = weekOfMonth;
        this.councilDay = day;
        this.councilTime = time;
        this.councilPlace = place;
    }
}
