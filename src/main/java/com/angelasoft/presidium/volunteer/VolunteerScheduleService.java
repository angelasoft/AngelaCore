package com.angelasoft.presidium.volunteer;

import com.angelasoft.presidium.PresidiumMembership;
import com.angelasoft.presidium.governance.GovernanceException;
import com.angelasoft.presidium.governance.MembershipLookup;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.ScheduleRequest;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.ScheduleResponse;
import com.angelasoft.presidium.volunteer.VolunteerScheduleDtos.SchedulePage;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봉사 일정. 단원은 조회만, 4간부는 등록·수정·삭제할 수 있다.
 * "다가오는 일정"은 한국 날짜 기준 오늘 이후(오늘 포함)다.
 */
@Service
@RequiredArgsConstructor
public class VolunteerScheduleService {

    static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE_SIZE = 50;

    private final MembershipLookup membershipLookup;
    private final VolunteerScheduleRepository scheduleRepository;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> upcoming(Long userId, int limit) {
        PresidiumMembership me = membershipLookup.require(userId);
        int size = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        return scheduleRepository
                .findByPresidiumIdAndServiceDateGreaterThanEqualOrderByServiceDateAscServiceTimeAsc(
                        me.getPresidium().getId(), today(), PageRequest.of(0, size))
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SchedulePage past(Long userId, int page, int size) {
        PresidiumMembership me = membershipLookup.require(userId);
        Slice<VolunteerSchedule> slice = scheduleRepository
                .findByPresidiumIdAndServiceDateLessThanOrderByServiceDateDescServiceTimeDesc(
                        me.getPresidium().getId(), today(),
                        PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE))));
        return new SchedulePage(slice.getContent().stream().map(ScheduleResponse::from).toList(), slice.hasNext());
    }

    @Transactional(readOnly = true)
    public ScheduleResponse get(Long userId, Long scheduleId) {
        PresidiumMembership me = membershipLookup.require(userId);
        return ScheduleResponse.from(find(me, scheduleId));
    }

    @Transactional
    public ScheduleResponse create(Long userId, ScheduleRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        validate(request);
        VolunteerSchedule saved = scheduleRepository.save(VolunteerSchedule.builder()
                .presidium(me.getPresidium())
                .serviceDate(request.serviceDate())
                .serviceTime(request.serviceTime())
                .place(request.place().trim())
                .content(request.content().trim())
                .createdBy(me.getUser())
                .build());
        return ScheduleResponse.from(saved);
    }

    @Transactional
    public ScheduleResponse update(Long userId, Long scheduleId, ScheduleRequest request) {
        PresidiumMembership me = requireOfficer(userId);
        validate(request);
        VolunteerSchedule schedule = find(me, scheduleId);
        schedule.update(request.serviceDate(), request.serviceTime(), request.place().trim(), request.content().trim());
        return ScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long userId, Long scheduleId) {
        PresidiumMembership me = requireOfficer(userId);
        scheduleRepository.delete(find(me, scheduleId));
    }

    private PresidiumMembership requireOfficer(Long userId) {
        PresidiumMembership me = membershipLookup.require(userId);
        if (!me.isOfficer()) {
            throw GovernanceException.forbidden("봉사 일정은 4간부만 등록·수정·삭제할 수 있습니다.");
        }
        return me;
    }

    private VolunteerSchedule find(PresidiumMembership me, Long scheduleId) {
        return scheduleRepository.findByIdAndPresidiumId(scheduleId, me.getPresidium().getId())
                .orElseThrow(() -> GovernanceException.notFound("봉사 일정을 찾을 수 없습니다."));
    }

    private static void validate(ScheduleRequest request) {
        if (request.serviceDate() == null || request.serviceTime() == null) {
            throw GovernanceException.badRequest("날짜와 시간을 입력해 주세요.");
        }
        if (request.place() == null || request.place().isBlank()) {
            throw GovernanceException.badRequest("장소를 입력해 주세요.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw GovernanceException.badRequest("봉사 내용을 입력해 주세요.");
        }
        if (request.place().trim().length() > 100) {
            throw GovernanceException.badRequest("장소는 100자 이내로 입력해 주세요.");
        }
        if (request.content().trim().length() > 1000) {
            throw GovernanceException.badRequest("봉사 내용은 1000자 이내로 입력해 주세요.");
        }
    }

    private static LocalDate today() {
        return LocalDate.now(KOREA);
    }
}
