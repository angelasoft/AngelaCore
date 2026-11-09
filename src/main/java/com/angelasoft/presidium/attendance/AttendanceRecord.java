package com.angelasoft.presidium.attendance;

import com.angelasoft.admin.user.User;
import com.angelasoft.presidium.OfficerRole;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석부의 단원 한 명. 간부·단원별 출석 집계를 위해 기록 당시 직책을 남긴다. */
@Entity
@Table(
        name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OfficerRole officerRole;

    @Column(nullable = false)
    private boolean present;

    AttendanceRecord(AttendanceSession session, User user, OfficerRole officerRole, boolean present) {
        this.session = session;
        this.user = user;
        this.officerRole = officerRole;
        this.present = present;
    }

    void update(OfficerRole officerRole, boolean present) {
        this.officerRole = officerRole;
        this.present = present;
    }

    public boolean isOfficer() {
        return officerRole != null;
    }
}
