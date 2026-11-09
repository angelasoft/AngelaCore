package com.angelasoft.presidium;

import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쁘레시디움 정보(설립일·승인일·주회합 요일/시간/장소), 휴회 주, 이번 주 주회합 차수.
 * 모든 단원이 보고, 4간부만 고칠 수 있다.
 */
@Service
@RequiredArgsConstructor
public class PresidiumProfileService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_PLACE_LENGTH = 100;
    private static final int MAX_REASON_LENGTH = 50;
    private static final int MAX_OFFSET = 5000;

    private final MembershipLookup membershipLookup;
    private final PresidiumRecessRepository recessRepository;

    public record ProfileResponse(
            Long presidiumId,
            LocalDate foundedDate,
            LocalDate approvedDate,
            DayOfWeek meetingDay,
            String meetingDayLabel,
            String meetingTime,
            String meetingPlace,
            int meetingNumberOffset,
            LocalDate thisWeekMeetingDate,
            /** 이번 주 주회합 차수. 휴회 주이거나 첫 회합 전이면 null. */
            Integer thisWeekMeetingNumber,
            boolean thisWeekRecess,
            String thisWeekRecessReason,
            /** 이번 주가 휴회면 다음 회합 차수. */
            Integer nextMeetingNumber,
            List<RecessResponse> recesses,
            CouncilResponse council,
            boolean canEdit
    ) {
    }

    /** 평의회(Cu) 월례회의. weekOfMonth 1~4, 5 는 마지막 주. 입력 전이면 값들이 null. */
    public record CouncilResponse(
            String curiaName,
            Integer weekOfMonth,
            DayOfWeek day,
            String scheduleLabel,
            String time,
            String place,
            LocalDate nextDate
    ) {
    }

    /** 시간은 "14:00". 요일·주차를 비우면 일정을 지운다. */
    public record CouncilRequest(Integer weekOfMonth, DayOfWeek day, String time, String place) {
    }

    public record RecessResponse(Long id, LocalDate weekStart, LocalDate weekEnd, String reason) {
    }

    /** 날짜는 "2026-10-26", 시간은 "19:30", 요일은 MONDAY~SUNDAY. 비운 값은 지운다. 보정값이 없으면 0. */
    public record UpdateRequest(
            String foundedDate,
            String approvedDate,
            DayOfWeek meetingDay,
            String meetingTime,
            String meetingPlace,
            Integer meetingNumberOffset
    ) {
    }

    /** [date] 가 속한 주(월~일)를 휴회로 등록한다. */
    public record RecessRequest(String date, String reason) {
    }

    /** 승인일부터 센 이번 주 차수. */
    public record MeetingCount(Integer thisWeekNumber, boolean thisWeekRecess, Integer nextNumber) {
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        return toResponse(me.getPresidium(), me.isOfficer());
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        LocalDate founded = parseDate(request.foundedDate(), "설립일");
        LocalDate approved = parseDate(request.approvedDate(), "승인일");
        if (founded != null && approved != null && approved.isBefore(founded)) {
            throw GovernanceException.badRequest("승인일은 설립일과 같거나 그 뒤여야 합니다.");
        }
        String place = request.meetingPlace() == null ? null : request.meetingPlace().strip();
        if (place != null && place.length() > MAX_PLACE_LENGTH) {
            throw GovernanceException.badRequest("회합 장소는 " + MAX_PLACE_LENGTH + "자까지 입력할 수 있습니다.");
        }
        int offset = request.meetingNumberOffset() == null ? 0 : request.meetingNumberOffset();
        if (Math.abs(offset) > MAX_OFFSET) {
            throw GovernanceException.badRequest("차수 보정값은 -" + MAX_OFFSET + " ~ " + MAX_OFFSET + " 사이로 입력해 주세요.");
        }
        Presidium presidium = me.getPresidium();
        presidium.updateProfile(founded, approved, request.meetingDay(), parseTime(request.meetingTime()),
                place == null || place.isEmpty() ? null : place, offset);
        return toResponse(presidium, true);
    }

    @Transactional
    public ProfileResponse addRecess(Long userId, RecessRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        LocalDate date = parseDate(request.date(), "휴회 날짜");
        if (date == null) {
            throw GovernanceException.badRequest("휴회할 주의 날짜를 골라 주세요.");
        }
        String reason = request.reason() == null ? null : request.reason().strip();
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw GovernanceException.badRequest("휴회 사유는 " + MAX_REASON_LENGTH + "자까지 입력할 수 있습니다.");
        }
        Presidium presidium = me.getPresidium();
        LocalDate week = weekStart(date);
        if (recessRepository.existsByPresidiumIdAndWeekStart(presidium.getId(), week)) {
            throw GovernanceException.conflict("이미 휴회로 등록된 주입니다.");
        }
        recessRepository.save(new PresidiumRecess(presidium, week, reason == null || reason.isEmpty() ? null : reason));
        return toResponse(presidium, true);
    }

    @Transactional
    public ProfileResponse removeRecess(Long userId, Long recessId) {
        PresidiumMembership me = requireOfficer(userId);
        Presidium presidium = me.getPresidium();
        PresidiumRecess recess = recessRepository.findByIdAndPresidiumId(recessId, presidium.getId())
                .orElseThrow(() -> GovernanceException.notFound("휴회 기록을 찾을 수 없습니다."));
        recessRepository.delete(recess);
        recessRepository.flush();
        return toResponse(presidium, true);
    }

    /** 같은 Cu 의 모든 Pr 이 함께 보는 일정이라 어느 Pr 의 4간부든 고칠 수 있다. */
    @Transactional
    public ProfileResponse updateCouncil(Long userId, CouncilRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        Integer week = request.weekOfMonth();
        DayOfWeek day = request.day();
        if ((week == null) != (day == null)) {
            throw GovernanceException.badRequest("월례회의 주차와 요일을 함께 골라 주세요.");
        }
        if (week != null && (week < 1 || week > MeetingCalendar.LAST_WEEK)) {
            throw GovernanceException.badRequest("주차는 첫째 주~넷째 주 또는 마지막 주로 골라 주세요.");
        }
        String place = request.place() == null ? null : request.place().strip();
        if (place != null && place.length() > MAX_PLACE_LENGTH) {
            throw GovernanceException.badRequest("장소는 " + MAX_PLACE_LENGTH + "자까지 입력할 수 있습니다.");
        }
        me.getPresidium().getCuria().updateCouncilSchedule(
                week, day, parseTime(request.time()), place == null || place.isEmpty() ? null : place);
        return toResponse(me.getPresidium(), true);
    }

    /** 이번 주 주회합 차수 (출석부 기록용). */
    @Transactional(readOnly = true)
    public MeetingCount meetingCountFor(Presidium p, LocalDate today) {
        Set<LocalDate> recessWeeks = recessWeeks(p.getId());
        return meetingCount(p.getApprovedDate(), p.getMeetingDay(), p.getMeetingNumberOffset(), recessWeeks, today);
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> recessWeeks(Long presidiumId) {
        return recessRepository.findByPresidiumIdOrderByWeekStartDesc(presidiumId).stream()
                .map(PresidiumRecess::getWeekStart)
                .collect(Collectors.toSet());
    }

    private PresidiumMembership requireOfficer(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("쁘레시디움 정보는 4간부만 수정할 수 있습니다.");
        }
        return me;
    }

    private ProfileResponse toResponse(Presidium p, boolean canEdit) {
        LocalDate today = LocalDate.now(KOREA);
        List<PresidiumRecess> recesses = recessRepository.findByPresidiumIdOrderByWeekStartDesc(p.getId());
        Set<LocalDate> recessWeeks = recesses.stream().map(PresidiumRecess::getWeekStart).collect(Collectors.toSet());
        MeetingCount count = meetingCount(
                p.getApprovedDate(), p.getMeetingDay(), p.getMeetingNumberOffset(), recessWeeks, today);
        LocalDate thisWeekStart = weekStart(today);
        String thisWeekReason = recesses.stream()
                .filter(r -> r.getWeekStart().equals(thisWeekStart))
                .map(PresidiumRecess::getReason)
                .findFirst()
                .orElse(null);
        return new ProfileResponse(
                p.getId(),
                p.getFoundedDate(),
                p.getApprovedDate(),
                p.getMeetingDay(),
                p.getMeetingDay() == null ? null : MeetingCalendar.dayLabel(p.getMeetingDay()),
                p.getMeetingTime() == null ? null : p.getMeetingTime().toString(),
                p.getMeetingPlace(),
                p.getMeetingNumberOffset(),
                p.getMeetingDay() == null ? null : thisWeekStart.with(TemporalAdjusters.nextOrSame(p.getMeetingDay())),
                count.thisWeekNumber(),
                count.thisWeekRecess(),
                thisWeekReason,
                count.nextNumber(),
                recesses.stream()
                        .map(r -> new RecessResponse(r.getId(), r.getWeekStart(), r.getWeekStart().plusDays(6), r.getReason()))
                        .toList(),
                council(p.getCuria(), today),
                canEdit);
    }

    private static CouncilResponse council(Curia curia, LocalDate today) {
        return new CouncilResponse(
                curia.getName(),
                curia.getCouncilWeekOfMonth(),
                curia.getCouncilDay(),
                MeetingCalendar.councilLabel(curia.getCouncilWeekOfMonth(), curia.getCouncilDay()),
                curia.getCouncilTime() == null ? null : curia.getCouncilTime().toString(),
                curia.getCouncilPlace(),
                MeetingCalendar.nextCouncilMeeting(curia.getCouncilWeekOfMonth(), curia.getCouncilDay(), today));
    }

    /**
     * 이번 주(월~일) 주회합 차수. 승인일 당일 또는 그 뒤 첫 회합 요일이 든 주가 제1차이고 매주 하나씩 센다.
     * 회합 요일을 정하지 않았으면 승인일이 든 주가 제1차. 휴회 주는 세지 않고, 마지막에 보정값을 더한다.
     * 이번 주가 휴회면 이번 주 차수는 없고 다음 회합 차수를 돌려준다.
     */
    static MeetingCount meetingCount(
            LocalDate approvedDate, DayOfWeek meetingDay, int offset, Collection<LocalDate> recessWeeks, LocalDate today) {
        LocalDate thisWeek = weekStart(today);
        boolean recess = recessWeeks.contains(thisWeek);
        if (approvedDate == null) {
            return new MeetingCount(null, recess, null);
        }
        LocalDate firstWeek = weekStart(meetingDay == null
                ? approvedDate
                : approvedDate.with(TemporalAdjusters.nextOrSame(meetingDay)));
        if (thisWeek.isBefore(firstWeek)) {
            return new MeetingCount(null, recess, null);
        }
        long weeks = ChronoUnit.WEEKS.between(firstWeek, thisWeek) + 1;
        long skipped = recessWeeks.stream()
                .filter(w -> !w.isBefore(firstWeek) && !w.isAfter(thisWeek))
                .count();
        int held = (int) (weeks - skipped) + offset;
        if (recess) {
            return new MeetingCount(null, true, Math.max(held + 1, 1));
        }
        return new MeetingCount(held >= 1 ? held : null, false, null);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw GovernanceException.badRequest(label + "은 2026-10-26 형식으로 보내 주세요.");
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw GovernanceException.badRequest("회합 시간은 19:30 형식으로 보내 주세요.");
        }
    }
}
