package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.TestResultStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CandidateTestResultDetailsDto(
        UUID resultId,
        UUID testId,
        String testName,
        /** TestType.name() — e.g. "PERSONALITY", "COGNITIVE". */
        String testType,
        TestResultStatus status,
        LocalDateTime completedAt,
        LocalDateTime scoredAt,
        int focusLossCount,
        /** Echoed visibility flags so Flutter knows what was redacted. */
        boolean rawScoreVisible,
        boolean stenProfileVisible,
        boolean percentileVisible,
        boolean scaleBreakdownVisible,
        /** Empty list when showScaleBreakdown=false per the visibility policy. */
        List<ScaleScoreDto> scales,
        /** True when the visibility policy grants access to the competency profile. */
        boolean competencyMapVisible,
        /** Empty list when competencyMapVisible=false. */
        List<CompetencyScoreDto> competencies
) {}
