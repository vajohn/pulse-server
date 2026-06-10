package com.edge.pulse.data.dto;
import com.edge.pulse.data.enums.QuestionType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AnswerDto(
    UUID submissionId,
    UUID questionId,
    QuestionType answerType,
    int version,
    boolean isCurrent,
    LocalDateTime submittedAt,
    // TEXT payload
    String textValue,
    // SCALE payload
    Integer scaleValue,
    Integer minValue,
    Integer maxValue,
    // CHOICE payload
    UUID candidateAnswerId,
    String candidateAnswerLabel,
    // RATING payload
    List<RatingEntry> ratings,
    // Cross-type optional comment
    String comment
) {
    public record RatingEntry(String subjectLabel, int stars, int maxStars) {}
}
