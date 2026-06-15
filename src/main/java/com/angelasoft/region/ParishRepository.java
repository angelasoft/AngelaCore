package com.angelasoft.region;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParishRepository extends JpaRepository<Parish, Long> {

    List<Parish> findByDistrictIdOrderByNameAsc(Long districtId);

    List<Parish> findByDistrictDioceseIdOrderByNameAsc(Long dioceseId);

    List<Parish> findTop30ByNameContainingOrderByNameAsc(String name);
}
