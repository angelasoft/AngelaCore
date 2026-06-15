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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 본당. 주소록 본당 코드(code)를 고유 키로 사용한다. */
@Entity
@Table(name = "parishes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Parish {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    /** 본당 / 준본당 / 주교좌 */
    @Column(nullable = false, length = 10)
    private String type;

    @Builder
    private Parish(District district, String code, String name, String type) {
        this.district = district;
        this.code = code;
        this.name = name;
        this.type = type;
    }
}
