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

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock PsychometricInstrumentRepository repo;
    @InjectMocks InstrumentService service;

    @Test
    void resolveOrCreate_linksExistingByCanonical_reusingDisplay() {
        PsychometricInstrument existing = PsychometricInstrument.builder()
                .id(UUID.randomUUID()).displayName("Big Five (in development)").canonicalName("big five").build();
        when(repo.findByCanonicalName("big five")).thenReturn(Optional.of(existing));

        PsychometricInstrument out = service.resolveOrCreate("Big-Five");

        assertThat(out).isSameAs(existing);
        assertThat(out.getDisplayName()).isEqualTo("Big Five (in development)"); // first writer's display wins
        verify(repo, never()).save(any());
    }

    @Test
    void resolveOrCreate_createsWhenCanonicalMissing() {
        when(repo.findByCanonicalName("novel scale")).thenReturn(Optional.empty());
        when(repo.save(any(PsychometricInstrument.class))).thenAnswer(inv -> inv.getArgument(0));

        PsychometricInstrument out = service.resolveOrCreate("  Novel  Scale ");

        assertThat(out.getDisplayName()).isEqualTo("Novel  Scale"); // trimmed display
        assertThat(out.getCanonicalName()).isEqualTo("novel scale");
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
        Object[] row = { id, "PTI Plus", "pti plus", 3L };
        when(repo.findAllWithTestCount()).thenReturn(List.<Object[]>of(row));
        var dtos = service.list();
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(id);
        assertThat(dtos.get(0).displayName()).isEqualTo("PTI Plus");
        assertThat(dtos.get(0).testCount()).isEqualTo(3);
    }
}
