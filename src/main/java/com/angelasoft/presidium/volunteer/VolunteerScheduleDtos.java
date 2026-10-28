package com.angelasoft.presidium.volunteer;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class VolunteerScheduleDtos {

    private VolunteerScheduleDtos() {
    }

    /** 날짜는 "2026-10-30", 시간은 "14:00" 형식. 한국 시간 기준 값을 그대로 주고받는다. */
    public record ScheduleRequest(
            LocalDate serviceDate,
            @JsonFormat(pattern = "HH:mm") LocalTime serviceTime,
            String place,
            String content
    ) {
    }

    public record ScheduleResponse(
            Long id,
            LocalDate serviceDate,
            @JsonFormat(pattern = "HH:mm") LocalTime serviceTime,
            String place,
            String content,
            String createdByName
    ) {
        static ScheduleResponse from(VolunteerSchedule s) {
            String name = s.getCreatedBy().getName();
            String baptismal = s.getCreatedBy().getBaptismalName();
            String createdBy = baptismal == null || baptismal.isBlank() ? name : name + "(" + baptismal + ")";
            return new ScheduleResponse(
                    s.getId(), s.getServiceDate(), s.getServiceTime(), s.getPlace(), s.getContent(), createdBy);
        }
    }

    public record SchedulePage(List<ScheduleResponse> items, boolean hasNext) {
    }
}
