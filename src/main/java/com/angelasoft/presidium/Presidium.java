package com.angelasoft.presidium;

import com.angelasoft.admin.user.User;
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
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쁘레시디움(Pr). 큐리아 소속이며 어른/소년소녀 구분이 있다. */
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

    /** 구분 도입 전 생성분은 DB 에 null 이며 어른 쁘레시디움으로 본다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PresidiumType type;

    /** 쁘레시디움 정보 (4간부가 입력). 입력 전에는 null. */
    private LocalDate foundedDate;

    /** 꾸리아 승인일. 주회합 차수를 이 날부터 센다. */
    private LocalDate approvedDate;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private DayOfWeek meetingDay;

    private LocalTime meetingTime;

    @Column(length = 100)
    private String meetingPlace;

    @Builder
    private Presidium(Curia curia, String name, User createdBy, PresidiumType type) {
        this.curia = curia;
        this.name = name;
        this.createdBy = createdBy;
        this.type = type == null ? PresidiumType.ADULT : type;
    }

    public PresidiumType getType() {
        return type == null ? PresidiumType.ADULT : type;
    }

    void assignCreator(User createdBy) {
        this.createdBy = createdBy;
    }

    public void updateProfile(
            LocalDate foundedDate, LocalDate approvedDate, DayOfWeek meetingDay, LocalTime meetingTime, String meetingPlace) {
        this.foundedDate = foundedDate;
        this.approvedDate = approvedDate;
        this.meetingDay = meetingDay;
        this.meetingTime = meetingTime;
        this.meetingPlace = meetingPlace;
    }
}
