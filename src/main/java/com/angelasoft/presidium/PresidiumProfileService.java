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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쁘레시디움 정보(설립일·승인일·주회합 요일/시간/장소)와 이번 주 주회합 차수.
 * 모든 단원이 보고, 4간부만 고칠 수 있다.
 */
@Service
@RequiredArgsConstructor
public class PresidiumProfileService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_PLACE_LENGTH = 100;

    private final MembershipLookup membershipLookup;

    public record ProfileResponse(
            Long presidiumId,
            LocalDate foundedDate,
            LocalDate approvedDate,
            DayOfWeek meetingDay,
            String meetingDayLabel,
            String meetingTime,
            String meetingPlace,
            LocalDate thisWeekMeetingDate,
            Integer thisWeekMeetingNumber,
            boolean canEdit
    ) {
    }

    /** 날짜는 "2026-10-26", 시간은 "19:30", 요일은 MONDAY~SUNDAY. 비운 값은 지운다. */
    public record UpdateRequest(
            String foundedDate,
            String approvedDate,
            DayOfWeek meetingDay,
            String meetingTime,
            String meetingPlace
    ) {
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        return toResponse(me.getPresidium(), me.isOfficer(), LocalDate.now(KOREA));
    }

    @Transactional
    public ProfileResponse update(Long userId, UpdateRequest request) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("쁘레시디움 정보는 4간부만 수정할 수 있습니다.");
        }
        LocalDate founded = parseDate(request.foundedDate(), "설립일");
        LocalDate approved = parseDate(request.approvedDate(), "승인일");
        if (founded != null && approved != null && approved.isBefore(founded)) {
            throw GovernanceException.badRequest("승인일은 설립일과 같거나 그 뒤여야 합니다.");
        }
        String place = request.meetingPlace() == null ? null : request.meetingPlace().strip();
        if (place != null && place.length() > MAX_PLACE_LENGTH) {
            throw GovernanceException.badRequest("회합 장소는 " + MAX_PLACE_LENGTH + "자까지 입력할 수 있습니다.");
        }
        Presidium presidium = me.getPresidium();
        presidium.updateProfile(founded, approved, request.meetingDay(), parseTime(request.meetingTime()),
                place == null || place.isEmpty() ? null : place);
        return toResponse(presidium, true, LocalDate.now(KOREA));
    }

    static ProfileResponse toResponse(Presidium p, boolean canEdit, LocalDate today) {
        LocalDate thisWeekMeeting = p.getMeetingDay() == null
                ? null
                : weekStart(today).with(TemporalAdjusters.nextOrSame(p.getMeetingDay()));
        return new ProfileResponse(
                p.getId(),
                p.getFoundedDate(),
                p.getApprovedDate(),
                p.getMeetingDay(),
                p.getMeetingDay() == null ? null : dayLabel(p.getMeetingDay()),
                p.getMeetingTime() == null ? null : p.getMeetingTime().toString(),
                p.getMeetingPlace(),
                thisWeekMeeting,
                meetingNumber(p.getApprovedDate(), p.getMeetingDay(), today),
                canEdit);
    }

    /**
     * 이번 주(월~일) 주회합이 승인 뒤 몇 번째인지. 승인일 당일 또는 그 뒤 첫 회합 요일이 제1차이고 매주 하나씩 센다.
     * 회합 요일을 정하지 않았으면 승인일이 든 주를 제1차로 센다. 승인 전이거나 승인일이 없으면 null.
     * (쉬는 주가 있었다면 실제 차수와 다를 수 있다.)
     */
    static Integer meetingNumber(LocalDate approvedDate, DayOfWeek meetingDay, LocalDate today) {
        if (approvedDate == null) {
            return null;
        }
        LocalDate first = meetingDay == null
                ? weekStart(approvedDate)
                : approvedDate.with(TemporalAdjusters.nextOrSame(meetingDay));
        LocalDate thisWeek = meetingDay == null
                ? weekStart(today)
                : weekStart(today).with(TemporalAdjusters.nextOrSame(meetingDay));
        if (thisWeek.isBefore(first)) {
            return null;
        }
        return (int) ChronoUnit.WEEKS.between(first, thisWeek) + 1;
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String dayLabel(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
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
