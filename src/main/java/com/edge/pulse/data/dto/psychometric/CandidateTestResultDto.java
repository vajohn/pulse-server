package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.ResultState;
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
        /** Accrual state (Phase 3): FINAL, PROVISIONAL (consolidated scales still accruing),
         *  or NOT_YET_SCOREABLE. Drives the candidate "building profile" completion copy. */
        ResultState resultState,
        LocalDateTime completedAt,
        LocalDateTime scoredAt,
        int focusLossCount
) {}
