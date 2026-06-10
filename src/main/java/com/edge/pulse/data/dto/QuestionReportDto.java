package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.QuestionType;

import java.util.List;
import java.util.UUID;

/**
 * Per-question analytics breakdown included in {@link SurveyReportDto}.
 * Fields are nullable based on question type — only the relevant fields
 * will be populated per type.
 */
public record QuestionReportDto(
        UUID questionId,
        String body,
        QuestionType questionType,
        int displayOrder,
        long responseCount,
        boolean privacyThresholdMet,

        // SCALE
        Double averageScale,
        List<ScoreEntry> scaleDistribution,

        // RATING
        Double averageRating,
        List<RatingSubjectEntry> ratingBySubject,

        // CHOICE
        List<ChoiceEntry> choiceDistribution,

        // TEXT
        Long textResponseCount,         // always populated when threshold met
        List<String> textResponses      // null when no permission; empty when threshold not met; populated otherwise
) {

    /** One bar in a scale distribution: score value → how many chose it. */
    public record ScoreEntry(int score, long count) {}

    /** Per-subject average for a RATING question. */
    public record RatingSubjectEntry(String subject, double averageStars) {}

    /** One option in a multiple-choice breakdown. */
    public record ChoiceEntry(String label, long count, double percentage) {}
}
