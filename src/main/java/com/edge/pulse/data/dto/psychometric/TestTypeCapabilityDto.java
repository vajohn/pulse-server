package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.Measures;
import java.util.List;

/**
 * Self-documenting catalog entry for a psychometric test type.
 * Single source of truth for the dashboard's type dropdown + tooltip
 * ({@code GET /api/admin/psychometric/test-types}).
 */
public record TestTypeCapabilityDto(
        /** TestType.name() — e.g. "PERSONALITY". */
        String value,
        String displayLabel,
        String description,
        Measures measures,
        List<String> exampleInstruments,
        boolean timeLimitRequired,
        boolean timeLimitVisible,
        /** QuestionType.name() values allowed for this type (empty for DERIVED types). */
        List<String> allowedQuestionTypes
) {}
