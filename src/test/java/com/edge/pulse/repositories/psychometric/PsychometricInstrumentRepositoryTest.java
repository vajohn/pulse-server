package com.edge.pulse.repositories.psychometric;

import static org.assertj.core.api.Assertions.assertThat;

import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link PsychometricInstrumentRepository}.
 *
 * <p>Requires running PostgreSQL with V12 migration applied.
 * Activate via: {@code SPRING_PROFILES_ACTIVE=local ./gradlew test --tests "...PsychometricInstrumentRepositoryTest"}
 */
@SpringBootTest
@ActiveProfiles("local")
class PsychometricInstrumentRepositoryTest {

    @Autowired
    PsychometricInstrumentRepository repo;

    @Test
    void findByCanonicalName_matchesSeed() {
        assertThat(repo.findByCanonicalName("bigfive")).isPresent();
        assertThat(repo.findByCanonicalName("nopenope")).isEmpty();
    }

    @Test
    void savesAndReadsBack() {
        PsychometricInstrument i = PsychometricInstrument.builder()
                .displayName("Test Widget").canonicalName("testwidget").build();
        PsychometricInstrument saved = repo.saveAndFlush(i);
        assertThat(saved.getId()).isNotNull();
        assertThat(repo.findByCanonicalName("testwidget")).isPresent();

        // Clean up the test data
        repo.delete(saved);
        repo.flush();
    }
}
