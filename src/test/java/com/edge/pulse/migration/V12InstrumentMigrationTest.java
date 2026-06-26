package com.edge.pulse.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration smoke-test that V12 was applied correctly.
 *
 * <p>Requires a running PostgreSQL (local dev DB) with all Flyway migrations applied.
 * Activate via: {@code SPRING_PROFILES_ACTIVE=local ./gradlew test --tests "...V12InstrumentMigrationTest"}
 */
@SpringBootTest
@ActiveProfiles("local")
class V12InstrumentMigrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void instrumentTableAndFkExist() {
        Integer cols = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'psychometric_instrument'", Integer.class);
        assertThat(cols).isGreaterThanOrEqualTo(4); // id, display_name, canonical_name, created_at

        Integer fk = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'psychometric_test' AND column_name = 'instrument_id'",
                Integer.class);
        assertThat(fk).isEqualTo(1);
    }

    @Test
    void seedIsPresentAndCanonicalUnique() {
        Integer big = jdbc.queryForObject(
                "SELECT count(*) FROM psychometric_instrument WHERE canonical_name = 'big five'",
                Integer.class);
        assertThat(big).isEqualTo(1);

        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM psychometric_instrument", Integer.class);
        assertThat(total).isGreaterThanOrEqualTo(10); // ten seeded instruments

        Integer uniqueIdx = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'psychometric_instrument' "
                        + "AND indexdef ILIKE '%canonical_name%' AND indexdef ILIKE '%UNIQUE%'",
                Integer.class);
        assertThat(uniqueIdx).isGreaterThanOrEqualTo(1);
    }
}
