package com.angelasoft.presidium;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuriaRepository extends JpaRepository<Curia, Long> {

    List<Curia> findByParishIdOrderByNameAsc(Long parishId);

    Optional<Curia> findByParishIdAndName(Long parishId, String name);
}
