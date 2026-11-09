package com.angelasoft.core.db;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate 가 표를 처음 만들 때 enum 컬럼마다 "허용 값 목록" CHECK 제약을 건다.
 * ddl-auto=update 는 이 제약을 고치지 않으므로 enum 에 값을 더하면 운영 DB 에서 저장이 실패한다.
 * 값 검사는 JPA(@Enumerated)가 이미 하므로, PostgreSQL 에서는 기동할 때 이런 제약을 지운다.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class EnumCheckConstraintCleaner implements ApplicationRunner {

    private static final String FIND_ENUM_CHECKS = """
            SELECT t.relname AS table_name, c.conname AS constraint_name
            FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
            WHERE c.contype = 'c'
              AND t.relnamespace = 'public'::regnamespace
              AND pg_get_constraintdef(c.oid) LIKE '%= ANY ((ARRAY[%'
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String database = jdbcTemplate.execute(
                (java.sql.Connection connection) -> connection.getMetaData().getDatabaseProductName());
        if (!"PostgreSQL".equalsIgnoreCase(database)) {
            return;
        }
        List<String[]> constraints = jdbcTemplate.query(FIND_ENUM_CHECKS,
                (rs, i) -> new String[] {rs.getString("table_name"), rs.getString("constraint_name")});
        for (String[] c : constraints) {
            jdbcTemplate.execute("ALTER TABLE \"" + c[0] + "\" DROP CONSTRAINT IF EXISTS \"" + c[1] + "\"");
        }
        if (!constraints.isEmpty()) {
            log.info("[EnumCheckConstraintCleaner] enum CHECK 제약 {}개 삭제", constraints.size());
        }
    }
}
