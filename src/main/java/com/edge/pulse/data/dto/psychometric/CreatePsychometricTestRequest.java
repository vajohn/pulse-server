package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.TestType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/admin/psychometric/tests}.
 *
 * <p>Creates a {@code Survey} (instrumentType=PSYCHOMETRIC) and a
 * {@code PsychometricTest} atomically in a single transaction.
 */
public record CreatePsychometricTestRequest(
        @NotBlank String name,
        String description,
        String instructions,
        @NotNull TestType testType,
        /** NULL = untimed personality test. Non-null = cognitive test with enforced deadline. */
        Integer timeLimitSecs
) {}
