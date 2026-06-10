package com.edge.pulse.data.dto.psychometric;

import java.util.List;
import java.util.UUID;

/**
 * Full session payload returned on psychometric session start or resume.
 *
 * <p>{@code timeLimitSecs} and {@code remainingSeconds} are {@code null} for untimed
 * (PERSONALITY / COMPETENCY) tests. The Flutter client must treat {@code null} as "no timer".
 *
 * <p>{@code itemSequence} defines the order in which questions should be presented.
 * The {@code questions} list is ordered to match {@code itemSequence}.
 */
public record PsychometricSessionDto(
        UUID sessionId,
        String testName,
        String testType,
        String instructions,
        Integer timeLimitSecs,
        Long remainingSeconds,
        Long serverStartEpoch,
        List<UUID> itemSequence,
        List<PsychometricQuestionDto> questions
) {}
