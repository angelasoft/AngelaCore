package com.angelasoft.presidium;

import com.angelasoft.region.Parish;
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

    @Builder
    private Curia(Parish parish, String name) {
        this.parish = parish;
        this.name = name;
    }
}
