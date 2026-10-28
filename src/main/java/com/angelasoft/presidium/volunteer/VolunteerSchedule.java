package com.angelasoft.presidium.volunteer;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.Presidium;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 쁘레시디움 봉사 일정. 4간부가 등록·수정·삭제한다. */
@Entity
@Table(
        name = "volunteer_schedules",
        indexes = @Index(name = "idx_volunteer_presidium_date", columnList = "presidium_id, service_date")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VolunteerSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Column(nullable = false)
    private LocalTime serviceTime;

    @Column(nullable = false, length = 100)
    private String place;

    @Column(nullable = false, length = 1000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private VolunteerSchedule(
            Presidium presidium,
            LocalDate serviceDate,
            LocalTime serviceTime,
            String place,
            String content,
            User createdBy
    ) {
        this.presidium = presidium;
        this.serviceDate = serviceDate;
        this.serviceTime = serviceTime;
        this.place = place;
        this.content = content;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    void update(LocalDate serviceDate, LocalTime serviceTime, String place, String content) {
        this.serviceDate = serviceDate;
        this.serviceTime = serviceTime;
        this.place = place;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}
