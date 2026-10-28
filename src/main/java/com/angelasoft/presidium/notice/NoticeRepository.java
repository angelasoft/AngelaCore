package com.angelasoft.presidium.notice;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** 최신 공지부터, 무한 스크롤용으로 잘라서 가져온다. */
    Slice<Notice> findByPresidiumIdOrderByCreatedAtDescIdDesc(Long presidiumId, Pageable pageable);

    Optional<Notice> findByIdAndPresidiumId(Long id, Long presidiumId);
}
