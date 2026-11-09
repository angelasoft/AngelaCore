package com.angelasoft.presidium.attendance;

import com.angelasoft.presidium.Curia;
import com.angelasoft.presidium.OfficerRole;
import com.angelasoft.presidium.Presidium;
import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.PresidiumMembershipRepository;
import com.angelasoft.presidium.PresidiumProfileService;
import com.angelasoft.presidium.governance.GovernanceDtos;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주회합·평의회 출석부. 회합 당일 회합 시간부터 그날 자정 전까지 4간부가 입력하고, 모든 단원이 본다.
 *
 * - 주회합: 행동단원(쁘레또리움 단원·간부 포함). 휴회 주에는 열리지 않는다.
 * - 평의회: 쁘레시디움 4간부. 같은 Cu 의 월례회의 일정(매달 N째 주 X요일)을 따른다.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int HISTORY_SIZE = 12;

    private final MembershipLookup membershipLookup;
    private final PresidiumMembershipRepository membershipRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final PresidiumProfileService profileService;

    public record Overview(
            AttendanceKind kind,
            String kindLabel,
            /** 회합 요일·시간이 입력돼 있는지. 없으면 쁘레시디움 정보에서 먼저 입력해야 한다. */
            boolean configured,
            boolean open,
            /** 열려 있으면 오늘, 아니면 다음 회합 날짜. */
            LocalDate meetingDate,
            String meetingTime,
            String meetingPlace,
            /** 주회합 차수 (열려 있을 때). */
            Integer meetingNumber,
            boolean canEdit,
            /** 오늘 출석부를 저장했는지. */
            boolean recorded,
            /** 열려 있을 때 오늘 명단. 저장 전이면 present 가 null. */
            List<Entry> roster,
            MonthSummary month,
            List<SessionSummary> history
    ) {
    }

    public record Entry(Long userId, String displayName, String officerLabel, Boolean present) {
    }

    /** 월례보고서의 "출석: 간부 13/16, 단원 11/16". */
    public record MonthSummary(
            int year, int month, int meetings, long officerPresent, long officerTotal, long memberPresent, long memberTotal) {
    }

    public record SessionSummary(
            Long id, LocalDate meetingDate, Integer meetingNumber, long presentCount, long totalCount) {
    }

    public record SessionDetail(Long id, AttendanceKind kind, LocalDate meetingDate, Integer meetingNumber, List<Entry> records) {
    }

    public record SaveRequest(List<Long> presentUserIds) {
    }

    @Transactional(readOnly = true)
    public Overview overview(Long userId, AttendanceKind kind) {
        PresidiumMembership me = membershipLookup.require(userId);
        return overview(me, requireKind(kind), LocalDateTime.now(KOREA));
    }

    @Transactional
    public Overview save(Long userId, AttendanceKind kind, SaveRequest request) {
        PresidiumMembership me = membershipLookup.require(userId);
        requireKind(kind);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("출석부는 4간부만 입력할 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now(KOREA);
        Presidium presidium = me.getPresidium();
        AttendanceWindow window = window(presidium, kind, now);
        if (!window.open()) {
            throw GovernanceException.badRequest(kind.getLabel() + " 출석부는 회합 당일 회합 시간부터 입력할 수 있습니다.");
        }

        Set<Long> present = request.presentUserIds() == null ? Set.of() : new HashSet<>(request.presentUserIds());
        List<PresidiumMembership> roster = roster(presidium.getId(), kind);
        Set<Long> rosterIds = roster.stream().map(m -> m.getUser().getId()).collect(Collectors.toSet());
        if (!rosterIds.containsAll(present)) {
            throw GovernanceException.badRequest("출석부 명단에 없는 단원이 있습니다.");
        }

        LocalDate today = now.toLocalDate();
        AttendanceSession session = findSession(presidium.getId(), kind, today)
                .orElseGet(() -> sessionRepository.save(new AttendanceSession(presidium, kind, today,
                        kind == AttendanceKind.PRESIDIUM
                                ? profileService.meetingCountFor(presidium, today).thisWeekNumber()
                                : null)));
        session.record(
                roster.stream()
                        .map(m -> new AttendanceSession.Entry(
                                m.getUser(), m.getOfficerRole(), present.contains(m.getUser().getId())))
                        .toList(),
                me.getUser());
        sessionRepository.flush();
        return overview(me, kind, now);
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(Long userId, Long sessionId) {
        PresidiumMembership me = membershipLookup.require(userId);
        AttendanceSession session = sessionRepository.findById(sessionId)
                .filter(s -> s.getPresidium().getId().equals(me.getPresidium().getId()))
                .orElseThrow(() -> GovernanceException.notFound("출석부를 찾을 수 없습니다."));
        List<Entry> records = session.getRecords().stream()
                .sorted(recordOrder())
                .map(r -> new Entry(r.getUser().getId(), GovernanceDtos.displayName(r.getUser()),
                        r.getOfficerRole() == null ? null : r.getOfficerRole().getLabel(), r.isPresent()))
                .toList();
        return new SessionDetail(session.getId(), session.getKind(), session.getMeetingDate(), session.getMeetingNumber(), records);
    }

    private Overview overview(PresidiumMembership me, AttendanceKind kind, LocalDateTime now) {
        Presidium presidium = me.getPresidium();
        AttendanceWindow window = window(presidium, kind, now);
        List<AttendanceSession> sessions = sessionRepository.findWithRecords(presidium.getId(), kind);
        LocalDate today = now.toLocalDate();
        Optional<AttendanceSession> todaySession = sessions.stream()
                .filter(s -> s.getMeetingDate().equals(today))
                .findFirst();

        List<Entry> roster = List.of();
        Integer meetingNumber = null;
        if (window.open()) {
            Map<Long, AttendanceRecord> saved = todaySession.map(s -> s.getRecords().stream()
                            .collect(Collectors.toMap(r -> r.getUser().getId(), Function.identity())))
                    .orElse(Map.of());
            roster = roster(presidium.getId(), kind).stream()
                    .map(m -> {
                        AttendanceRecord record = saved.get(m.getUser().getId());
                        return new Entry(m.getUser().getId(), GovernanceDtos.displayName(m.getUser()),
                                m.getOfficerRole() == null ? null : m.getOfficerRole().getLabel(),
                                record == null ? null : record.isPresent());
                    })
                    .toList();
            meetingNumber = todaySession.map(AttendanceSession::getMeetingNumber).orElseGet(() ->
                    kind == AttendanceKind.PRESIDIUM
                            ? profileService.meetingCountFor(presidium, today).thisWeekNumber()
                            : null);
        }

        return new Overview(
                kind,
                kind.getLabel(),
                window.configured(),
                window.open(),
                window.meetingDate(),
                window.meetingTime() == null ? null : window.meetingTime().toString(),
                kind == AttendanceKind.PRESIDIUM ? presidium.getMeetingPlace() : presidium.getCuria().getCouncilPlace(),
                meetingNumber,
                window.open() && me.isOfficer(),
                todaySession.isPresent(),
                roster,
                monthSummary(sessions, YearMonth.from(today)),
                sessions.stream()
                        .limit(HISTORY_SIZE)
                        .map(s -> new SessionSummary(s.getId(), s.getMeetingDate(), s.getMeetingNumber(),
                                s.getRecords().stream().filter(AttendanceRecord::isPresent).count(),
                                s.getRecords().size()))
                        .toList());
    }

    static MonthSummary monthSummary(Collection<AttendanceSession> sessions, YearMonth month) {
        List<AttendanceRecord> records = sessions.stream()
                .filter(s -> YearMonth.from(s.getMeetingDate()).equals(month))
                .flatMap(s -> s.getRecords().stream())
                .toList();
        int meetings = (int) sessions.stream().filter(s -> YearMonth.from(s.getMeetingDate()).equals(month)).count();
        return new MonthSummary(month.getYear(), month.getMonthValue(), meetings,
                records.stream().filter(r -> r.isOfficer() && r.isPresent()).count(),
                records.stream().filter(AttendanceRecord::isOfficer).count(),
                records.stream().filter(r -> !r.isOfficer() && r.isPresent()).count(),
                records.stream().filter(r -> !r.isOfficer()).count());
    }

    private AttendanceWindow window(Presidium presidium, AttendanceKind kind, LocalDateTime now) {
        if (kind == AttendanceKind.PRESIDIUM) {
            return AttendanceWindow.presidium(presidium.getMeetingDay(), presidium.getMeetingTime(),
                    profileService.recessWeeks(presidium.getId()), now);
        }
        Curia curia = presidium.getCuria();
        return AttendanceWindow.council(curia.getCouncilWeekOfMonth(), curia.getCouncilDay(), curia.getCouncilTime(), now);
    }

    /** 주회합은 행동단원(간부 포함), 평의회는 4간부. 간부가 먼저(단장·부단장·서기·회계), 그다음 가입 순. */
    private List<PresidiumMembership> roster(Long presidiumId, AttendanceKind kind) {
        return membershipRepository.findByPresidiumIdWithUser(presidiumId).stream()
                .filter(m -> kind == AttendanceKind.COUNCIL ? m.isOfficer() : m.getGrade().isActiveMember())
                .sorted(Comparator.comparing((PresidiumMembership m) -> officerOrder(m.getOfficerRole())))
                .toList();
    }

    private static Comparator<AttendanceRecord> recordOrder() {
        return Comparator.comparing((AttendanceRecord r) -> officerOrder(r.getOfficerRole()))
                .thenComparing(AttendanceRecord::getId);
    }

    private static int officerOrder(OfficerRole role) {
        return role == null ? OfficerRole.values().length : role.ordinal();
    }

    private Optional<AttendanceSession> findSession(Long presidiumId, AttendanceKind kind, LocalDate date) {
        return sessionRepository.findWithRecords(presidiumId, kind).stream()
                .filter(s -> s.getMeetingDate().equals(date))
                .findFirst();
    }

    private static AttendanceKind requireKind(AttendanceKind kind) {
        if (kind == null) {
            throw GovernanceException.badRequest("출석부 종류(PRESIDIUM 또는 COUNCIL)를 정해 주세요.");
        }
        return kind;
    }
}
