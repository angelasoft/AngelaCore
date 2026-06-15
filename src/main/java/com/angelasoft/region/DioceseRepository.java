package com.angelasoft.region;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DioceseRepository extends JpaRepository<Diocese, Long> {

    Optional<Diocese> findByCode(String code);

    List<Diocese> findAllByOrderByIdAsc();
}
