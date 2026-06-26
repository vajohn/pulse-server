package com.edge.pulse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke-test that V13 was applied correctly.
 *
 * <p>Requires a running PostgreSQL (local dev DB) with all Flyway migrations applied.
 * Activate via: {@code SPRING_PROFILES_ACTIVE=local ./gradlew test --tests "...V13ApprovalMigrationTest"}
 */
@SpringBootTest
@ActiveProfiles("local")
class V13ApprovalMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void tableAndColumnExist() {
        Integer table = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name='test_approval_request'",
            Integer.class);
        assertEquals(1, table);

        Integer col = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name='psychometric_test' AND column_name='supersedes_id'",
            Integer.class);
        assertEquals(1, col);
    }
}
