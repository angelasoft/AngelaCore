package com.angelasoft.activity;

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

/**
 * 성모님께 보고하는 활동 항목 (묵주기도, 미사영성체 등).
 *
 * 목록에서 빠진 활동은 지난 보고 기록을 보존하기 위해 삭제하지 않고 비활성화한다.
 */
@Entity
@Table(name = "activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @Builder
    private Activity(String name, int sortOrder) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    void activate(int sortOrder) {
        this.sortOrder = sortOrder;
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }
}
