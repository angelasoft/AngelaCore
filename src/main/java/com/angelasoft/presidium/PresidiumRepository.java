package com.angelasoft.presidium;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresidiumRepository extends JpaRepository<Presidium, Long> {

    Optional<Presidium> findByCuriaIdAndName(Long curiaId, String name);

    @Query("""
            SELECT p FROM Presidium p
            JOIN FETCH p.curia c
            JOIN FETCH c.parish
            WHERE p.id = :id
            """)
    Optional<Presidium> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT p FROM Presidium p
            JOIN FETCH p.curia c
            JOIN FETCH c.parish
            WHERE c.parish.id = :parishId
            ORDER BY c.name ASC, p.name ASC
            """)
    List<Presidium> findByParishIdOrderByCuriaAndName(@Param("parishId") Long parishId);
}
