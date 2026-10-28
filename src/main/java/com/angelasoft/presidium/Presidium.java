package com.angelasoft.presidium;

import com.angelasoft.admin.user.User;
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

    /** 쁘레시디움을 만든 사용자 (첫 단장). 기능 도입 전 생성분은 백필 전까지 null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @Builder
    private Presidium(Curia curia, String name, User createdBy) {
        this.curia = curia;
        this.name = name;
        this.createdBy = createdBy;
    }

    void assignCreator(User createdBy) {
        this.createdBy = createdBy;
    }
}
