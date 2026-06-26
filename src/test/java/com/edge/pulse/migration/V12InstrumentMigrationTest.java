package com.edge.pulse.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.edge.pulse.util.InstrumentNormalizer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

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
                "SELECT count(*) FROM psychometric_instrument WHERE canonical_name = 'bigfive'",
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

    /**
     * Seed invariant: for every row, canonical_name must equal
     * {@link InstrumentNormalizer#canonical(String)} applied to display_name.
     * This catches any future seed typo that would make the normalizer diverge from the DB.
     */
    @Test
    void seedRows_canonicalMatchesNormalizerOutputOfDisplayName() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT display_name, canonical_name FROM psychometric_instrument");

        assertThat(rows).as("seed must contain at least 10 rows").hasSizeGreaterThanOrEqualTo(10);

        for (Map<String, Object> row : rows) {
            String displayName   = (String) row.get("display_name");
            String canonicalName = (String) row.get("canonical_name");
            String expected      = InstrumentNormalizer.canonical(displayName);

            assertThat(canonicalName)
                    .as("canonical_name for display '%s' must equal InstrumentNormalizer.canonical('%s') = '%s'",
                            displayName, displayName, expected)
                    .isEqualTo(expected);
        }
    }
}
