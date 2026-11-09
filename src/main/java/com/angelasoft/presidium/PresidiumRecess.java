package com.angelasoft.presidium;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주회합을 쉰 주 (휴회). 이 주는 주회합 차수에서 빠진다. */
@Entity
@Table(
        name = "presidium_recesses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"presidium_id", "week_start"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PresidiumRecess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    /** 휴회한 주의 월요일. */
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(length = 50)
    private String reason;

    public PresidiumRecess(Presidium presidium, LocalDate weekStart, String reason) {
        this.presidium = presidium;
        this.weekStart = weekStart;
        this.reason = reason;
    }
}
