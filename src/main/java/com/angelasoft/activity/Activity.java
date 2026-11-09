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

    /**
     * 단원이 등록을 요청해 4간부 승인으로 추가된 활동. CSV 동기화가 비활성화하지 않는다.
     * 기능 도입 전 데이터는 DB 에 null 이며 CSV 활동으로 본다.
     */
    @Column(name = "is_custom")
    private Boolean custom;

    @Builder
    private Activity(String name, int sortOrder, boolean custom) {
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = true;
        this.custom = custom;
    }

    public boolean isCustom() {
        return Boolean.TRUE.equals(custom);
    }

    /** 공백을 빼고 비교한다 (예: "가정 성화" = "가정성화"). */
    public static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }

    void activate(int sortOrder) {
        this.sortOrder = sortOrder;
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }

    /** CSV 에서 빠진 활동을 단원 요청으로 다시 넣을 때. 이후 CSV 동기화가 비활성화하지 않는다. */
    void activateAsCustom(int sortOrder) {
        activate(sortOrder);
        this.custom = true;
    }
}
