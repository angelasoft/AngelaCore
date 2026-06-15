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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쁘레시디움(Pr). 큐리아 소속. */
@Entity
@Table(
        name = "presidiums",
        uniqueConstraints = @UniqueConstraint(columnNames = {"curia_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Presidium {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curia_id", nullable = false)
    private Curia curia;

    @Column(nullable = false, length = 100)
    private String name;

    /** 회합 회차. 신규 생성 시 1부터 시작. */
    @Column(nullable = false)
    private int sessionNumber;

    @Builder
    private Presidium(Curia curia, String name, int sessionNumber) {
        this.curia = curia;
        this.name = name;
        this.sessionNumber = sessionNumber;
    }
}
