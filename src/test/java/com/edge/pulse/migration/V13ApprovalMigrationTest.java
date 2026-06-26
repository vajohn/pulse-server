package com.edge.pulse.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration smoke-test that V13 was applied correctly.
 *
 * <p>Requires a running PostgreSQL (local dev DB) with all Flyway migrations applied.
 * Activate via: {@code SPRING_PROFILES_ACTIVE=local ./gradlew test --tests "...V13ApprovalMigrationTest"}
 */
@SpringBootTest
@ActiveProfiles("local")
class V13ApprovalMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void tableAndColumnExist() {
        Integer table = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name='test_approval_request'",
                Integer.class);
        assertThat(table).isEqualTo(1);

        Integer col = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name='psychometric_test' AND column_name='supersedes_id'",
                Integer.class);
        assertThat(col).isEqualTo(1);
    }

    /**
     * Regression guard: chk_test_status must include PENDING_APPROVAL.
     *
     * <p>Before V13's constraint widening, submitting a test for approval caused a 500 because
     * PENDING_APPROVAL was present in the Java TestStatus enum but absent from the DB CHECK
     * (which only allowed DRAFT/ACTIVE/RETIRED). This test verifies the constraint definition
     * directly via the catalog so it works without needing FK-satisfying fixture data.
     */
    @Test
    void chkTestStatus_includesPendingApproval() {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = 'psychometric_test'::regclass "
                        + "AND conname = 'chk_test_status'",
                String.class);

        assertThat(definition)
                .as("chk_test_status must contain all four TestStatus enum values")
                .contains("DRAFT")
                .contains("PENDING_APPROVAL")
                .contains("ACTIVE")
                .contains("RETIRED");
    }

    /**
     * Guard: chk_test_status must NOT include values that are not in TestStatus —
     * catches any future enum/migration desync where a stale value leaks in.
     */
    @Test
    void chkTestStatus_doesNotContainObsoleteValues() {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = 'psychometric_test'::regclass "
                        + "AND conname = 'chk_test_status'",
                String.class);

        // ARCHIVED and PUBLISHED were never valid TestStatus values; their presence
        // would indicate the constraint has been incorrectly widened.
        assertThat(definition)
                .as("chk_test_status must not contain ARCHIVED (not a TestStatus value)")
                .doesNotContain("ARCHIVED");
        assertThat(definition)
                .as("chk_test_status must not contain PUBLISHED (not a TestStatus value)")
                .doesNotContain("PUBLISHED");
    }

    /**
     * Guard: chk_approval_status (added in the same V13 widening) must cover all three
     * TestApprovalStatus values: PENDING, APPROVED, REJECTED.
     */
    @Test
    void chkApprovalStatus_coversAllTestApprovalStatusValues() {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conrelid = 'test_approval_request'::regclass "
                        + "AND conname = 'chk_approval_status'",
                String.class);

        assertThat(definition)
                .as("chk_approval_status must cover all TestApprovalStatus values")
                .contains("PENDING")
                .contains("APPROVED")
                .contains("REJECTED");
    }
}
