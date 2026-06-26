package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.InstrumentDto;
import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import com.edge.pulse.repositories.psychometric.PsychometricInstrumentRepository;
import com.edge.pulse.util.InstrumentNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
     *
     * <p>Race-safe: if a concurrent writer inserts the same canonical between our read and our
     * save, the UNIQUE constraint fires a {@link DataIntegrityViolationException}. We catch that
     * and re-read the now-existing row rather than propagating a 500 (BUG-002/003 pattern).
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
                .orElseGet(() -> {
                    try {
                        return repository.save(PsychometricInstrument.builder()
                                .displayName(displayString.trim())
                                .canonicalName(canonical)
                                .build());
                    } catch (DataIntegrityViolationException ex) {
                        // Concurrent insert won the race — re-read the winner's row.
                        return repository.findByCanonicalName(canonical)
                                .orElseThrow(() -> ex);
                    }
                });
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
