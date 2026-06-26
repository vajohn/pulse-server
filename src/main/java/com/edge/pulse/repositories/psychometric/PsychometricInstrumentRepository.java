package com.edge.pulse.repositories.psychometric;

import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PsychometricInstrumentRepository extends JpaRepository<PsychometricInstrument, UUID> {

    Optional<PsychometricInstrument> findByCanonicalName(String canonicalName);

    /** Instrument id + how many tests reference it (for the suggestions endpoint). */
    @Query("""
            SELECT i.id, i.displayName, i.canonicalName, COUNT(t.id)
            FROM PsychometricInstrument i
            LEFT JOIN PsychometricTest t ON t.instrument = i
            GROUP BY i.id, i.displayName, i.canonicalName
            ORDER BY i.displayName
            """)
    List<Object[]> findAllWithTestCount();
}
