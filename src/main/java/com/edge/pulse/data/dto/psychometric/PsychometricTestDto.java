package com.edge.pulse.data.dto.psychometric;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin read DTO for a psychometric test.
 *
 * <p>Superset of {@link CandidateTestDto} — includes status, version, and
 * question/scale counts which the admin UI needs but candidates do not.
 */
public record PsychometricTestDto(
        UUID testId,
        UUID surveyId,
        String name,
        String description,
        String instructions,
        /** TestType.name() — e.g. "PERSONALITY", "COGNITIVE". */
        String testType,
        /** NULL = untimed. */
        Integer timeLimitSecs,
        /** TestStatus.name() — e.g. "DRAFT", "ACTIVE", "RETIRED". */
        String status,
        int version,
        LocalDateTime createdAt,
        int questionCount,
        int scaleCount,
        /** Instrument display name (e.g. "Big Five"), or null if none. */
        String instrument,
        /** Instrument row id, or null if none. */
        UUID instrumentId
) {}
