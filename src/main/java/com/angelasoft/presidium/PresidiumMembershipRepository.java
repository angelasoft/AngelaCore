package com.angelasoft.presidium;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresidiumMembershipRepository extends JpaRepository<PresidiumMembership, Long> {

    Optional<PresidiumMembership> findByUserId(Long userId);

    @Query("""
            SELECT m FROM PresidiumMembership m
            JOIN FETCH m.presidium p
            JOIN FETCH p.curia c
            JOIN FETCH c.parish
            WHERE m.user.id = :userId
            """)
    Optional<PresidiumMembership> findByUserIdWithDetails(@Param("userId") Long userId);
}
