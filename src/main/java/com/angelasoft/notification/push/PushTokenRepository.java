package com.angelasoft.notification.push;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    List<PushToken> findByUserIdIn(Collection<Long> userIds);

    @Transactional
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);

    @Transactional
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.userId = :userId AND t.token = :token")
    int deleteByUserIdAndToken(@Param("userId") Long userId, @Param("token") String token);
}
