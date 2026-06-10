package com.edge.pulse.data.dto.psychometric;

import java.util.UUID;

public record CandidateTestDto(
        UUID testId,
        UUID formId,
        String name,
        String description,
        String instructions,
        /** TestType.name() — e.g. "PERSONALITY", "COGNITIVE". */
        String testType,
        /** NULL = untimed; non-null = enforced time limit in seconds. */
        Integer timeLimitSecs,
        int questionCount
) {}
