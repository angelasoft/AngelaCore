package com.angelasoft.presidium.attendance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.Presidium;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회합 하루치 출석부. 쁘레시디움·종류·날짜마다 하나. */
@Entity
@Table(
        name = "attendance_sessions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"presidium_id", "kind", "meeting_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presidium_id", nullable = false)
    private Presidium presidium;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceKind kind;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    /** 주회합 차수 (기록 당시 계산값). 평의회는 null. */
    private Integer meetingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id")
    private User recordedBy;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AttendanceRecord> records = new ArrayList<>();

    AttendanceSession(Presidium presidium, AttendanceKind kind, LocalDate meetingDate, Integer meetingNumber) {
        this.presidium = presidium;
        this.kind = kind;
        this.meetingDate = meetingDate;
        this.meetingNumber = meetingNumber;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 명단대로 출석을 기록한다. 같은 단원의 기록은 지우고 다시 넣지 않고 고쳐 쓴다
     * (Hibernate 는 삭제보다 추가를 먼저 반영해 (세션, 단원) 유일 제약에 걸린다). 명단에서 빠진 단원의 기록은 지운다.
     */
    void record(List<Entry> roster, User recordedBy) {
        Map<Long, AttendanceRecord> existing = records.stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), Function.identity()));
        List<AttendanceRecord> next = new ArrayList<>();
        for (Entry entry : roster) {
            AttendanceRecord record = existing.get(entry.user().getId());
            if (record == null) {
                record = new AttendanceRecord(this, entry.user(), entry.officerRole(), entry.present());
            } else {
                record.update(entry.officerRole(), entry.present());
            }
            next.add(record);
        }
        records.retainAll(next);
        next.stream().filter(r -> !records.contains(r)).forEach(records::add);
        this.recordedBy = recordedBy;
        this.updatedAt = LocalDateTime.now();
    }

    record Entry(User user, OfficerRole officerRole, boolean present) {
    }
}
