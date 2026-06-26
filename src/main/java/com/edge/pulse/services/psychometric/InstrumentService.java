package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.InstrumentDto;
import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import com.edge.pulse.repositories.psychometric.PsychometricInstrumentRepository;
import com.edge.pulse.util.InstrumentNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InstrumentService {

    private final PsychometricInstrumentRepository repository;

    /**
     * Resolves a free-text instrument display string to a {@link PsychometricInstrument}, creating
     * one if no row shares its canonical form. Returns {@code null} for null/blank/separator-only
     * input (the test simply has no instrument). When an existing row matches, the existing display
     * name is preserved (first writer wins) so labels stay consistent.
     */
    @Transactional
    public PsychometricInstrument resolveOrCreate(String displayString) {
        if (displayString == null) {
            return null;
        }
        String canonical = InstrumentNormalizer.canonical(displayString);
        if (canonical == null || canonical.isEmpty()) {
            return null;
        }
        return repository.findByCanonicalName(canonical)
                .orElseGet(() -> repository.save(PsychometricInstrument.builder()
                        .displayName(displayString.trim())
                        .canonicalName(canonical)
                        .build()));
    }

    @Transactional(readOnly = true)
    public List<InstrumentDto> list() {
        return repository.findAllWithTestCount().stream()
                .map(row -> new InstrumentDto(
                        (java.util.UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).longValue()))
                .toList();
    }
}
