package com.edge.pulse.services.psychometric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import com.edge.pulse.repositories.psychometric.PsychometricInstrumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock PsychometricInstrumentRepository repo;
    @InjectMocks InstrumentService service;

    @Test
    void resolveOrCreate_linksExistingByCanonical_reusingDisplay() {
        PsychometricInstrument existing = PsychometricInstrument.builder()
                .id(UUID.randomUUID()).displayName("Big Five").canonicalName("bigfive").build();
        when(repo.findByCanonicalName("bigfive")).thenReturn(Optional.of(existing));

        PsychometricInstrument out = service.resolveOrCreate("Big-Five");

        assertThat(out).isSameAs(existing);
        assertThat(out.getDisplayName()).isEqualTo("Big Five"); // first writer's display wins
        verify(repo, never()).save(any());
    }

    @Test
    void resolveOrCreate_linksExistingByCanonical_noSeparatorVariant() {
        // Regression: "bigfive" (no separator) must resolve the same row as "Big Five"
        PsychometricInstrument existing = PsychometricInstrument.builder()
                .id(UUID.randomUUID()).displayName("Big Five").canonicalName("bigfive").build();
        when(repo.findByCanonicalName("bigfive")).thenReturn(Optional.of(existing));

        PsychometricInstrument out = service.resolveOrCreate("bigfive");

        assertThat(out).isSameAs(existing);
        verify(repo, never()).save(any());
    }

    @Test
    void resolveOrCreate_raceCondition_catchesConstraintViolationAndRereads() {
        // Simulate: read misses, save throws UNIQUE constraint, re-read returns winner
        PsychometricInstrument winner = PsychometricInstrument.builder()
                .id(UUID.randomUUID()).displayName("Big Five").canonicalName("bigfive").build();
        when(repo.findByCanonicalName("bigfive"))
                .thenReturn(Optional.empty())   // first read — race window
                .thenReturn(Optional.of(winner)); // re-read after constraint violation
        when(repo.save(any(PsychometricInstrument.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key: canonical_name"));

        PsychometricInstrument out = service.resolveOrCreate("Big Five");

        assertThat(out).isSameAs(winner);
        verify(repo).save(any(PsychometricInstrument.class));
    }

    @Test
    void resolveOrCreate_createsWhenCanonicalMissing() {
        when(repo.findByCanonicalName("novelscale")).thenReturn(Optional.empty());
        when(repo.save(any(PsychometricInstrument.class))).thenAnswer(inv -> inv.getArgument(0));

        PsychometricInstrument out = service.resolveOrCreate("  Novel  Scale ");

        assertThat(out.getDisplayName()).isEqualTo("Novel  Scale"); // trimmed display
        assertThat(out.getCanonicalName()).isEqualTo("novelscale");
        verify(repo).save(any(PsychometricInstrument.class));
    }

    @Test
    void resolveOrCreate_nullOrBlankReturnsNull() {
        assertThat(service.resolveOrCreate(null)).isNull();
        assertThat(service.resolveOrCreate("   ")).isNull();
        assertThat(service.resolveOrCreate("---")).isNull(); // canonical empty
        verify(repo, never()).save(any());
    }

    @Test
    void list_mapsRowsWithTestCount() {
        UUID id = UUID.randomUUID();
        Object[] row = { id, "PTI Plus", "ptiplus", 3L };
        when(repo.findAllWithTestCount()).thenReturn(List.<Object[]>of(row));
        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(id);
        assertThat(dtos.get(0).displayName()).isEqualTo("PTI Plus");
        assertThat(dtos.get(0).testCount()).isEqualTo(3);
    }
}
