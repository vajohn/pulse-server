package com.edge.pulse.data.dto.psychometric;

import java.util.UUID;

/** Instrument suggestion entry for {@code GET /api/admin/psychometric/instruments}. */
public record InstrumentDto(
        UUID id,
        String displayName,
        String canonicalName,
        long testCount
) {}
