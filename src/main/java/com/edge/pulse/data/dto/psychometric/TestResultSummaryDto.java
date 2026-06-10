package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.TestResultStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record TestResultSummaryDto(
        UUID resultId,
        UUID testId,
        UUID sessionId,
        UUID userId,
        String userName,
        String testName,
        TestResultStatus status,
        LocalDateTime scoredAt,
        LocalDateTime reviewedAt,
        String reviewNotes,
        int focusLossCount,
        UUID scoringKeyVersionId,
        UUID normTableVersionId
) {}
