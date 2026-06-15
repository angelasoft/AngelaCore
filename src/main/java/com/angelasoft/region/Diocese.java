package com.angelasoft.region;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 교구. 주교회의 주소록의 교구 코드(gyogu)를 고유 키로 사용한다. */
@Entity
@Table(name = "dioceses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diocese {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder
    private Diocese(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
