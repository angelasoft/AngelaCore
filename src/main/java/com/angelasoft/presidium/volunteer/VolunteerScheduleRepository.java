package com.angelasoft.presidium.volunteer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerScheduleRepository extends JpaRepository<VolunteerSchedule, Long> {

    /** 오늘 이후 일정. 가까운 날짜·시간 순. */
    List<VolunteerSchedule> findByPresidiumIdAndServiceDateGreaterThanEqualOrderByServiceDateAscServiceTimeAsc(
            Long presidiumId, LocalDate today, Pageable pageable);

    /** 지난 일정. 최근 날짜부터, 무한 스크롤용으로 잘라서 가져온다. */
    Slice<VolunteerSchedule> findByPresidiumIdAndServiceDateLessThanOrderByServiceDateDescServiceTimeDesc(
            Long presidiumId, LocalDate today, Pageable pageable);

    Optional<VolunteerSchedule> findByIdAndPresidiumId(Long id, Long presidiumId);
}
