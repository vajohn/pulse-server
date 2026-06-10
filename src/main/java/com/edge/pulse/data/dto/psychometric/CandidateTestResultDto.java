package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.TestResultStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CandidateTestResultDto(
        UUID resultId,
        UUID testId,
        String testName,
        /** TestType.name() — e.g. "PERSONALITY", "COGNITIVE". */
        String testType,
        TestResultStatus status,
        LocalDateTime completedAt,
        LocalDateTime scoredAt,
        int focusLossCount
) {}
