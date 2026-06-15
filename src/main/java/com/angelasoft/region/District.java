package com.angelasoft.region;

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

/**
 * 교구 하위 중간 계층. 교구마다 단위가 다르다(지구/대리구/지역).
 * 컬럼명은 범용적으로 district로 둔다.
 */
@Entity
@Table(
        name = "districts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"diocese_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diocese_id", nullable = false)
    private Diocese diocese;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder
    private District(Diocese diocese, String name) {
        this.diocese = diocese;
        this.name = name;
    }
}
