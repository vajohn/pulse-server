package com.edge.pulse.data.dto;
import com.edge.pulse.data.enums.QuestionType;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record SubmitAnswerRequest(
    @NotNull UUID questionId,
    @NotNull QuestionType answerType,
    String textValue,
    Integer scaleValue,
    Integer minValue,
    Integer maxValue,
    UUID candidateAnswerId,
    // RATING payload — list of subject ratings for a single question
    List<RatingEntry> ratings,
    String comment,
    // ADJECTIVE_CHECKLIST payload — selected adjective label strings
    List<String> selectedAdjectives
) {
    public record RatingEntry(String subjectLabel, int stars, Integer maxStars) {}
}
