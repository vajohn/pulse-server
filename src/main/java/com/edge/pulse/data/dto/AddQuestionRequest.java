package com.edge.pulse.data.dto;
import com.edge.pulse.data.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AddQuestionRequest(
    @NotBlank String body,
    /** Arabic translation of body. Null when not yet provided. */
    String bodyAr,
    @NotNull QuestionType questionType,
    LocalDateTime effectiveDate,
    LocalDateTime expirationDate,
    int displayOrder,
    List<CandidateAnswerDto> candidateAnswers,
    List<String> subjectLabels,
    /** Arabic translations of subjectLabels (positional parallel). Null when not yet provided. */
    List<String> subjectLabelsAr,
    Integer scaleMin,
    Integer scaleMax,
    String minLabel,
    /** Arabic translation of minLabel. Null when not yet provided. */
    String minLabelAr,
    String maxLabel,
    /** Arabic translation of maxLabel. Null when not yet provided. */
    String maxLabelAr,
    /**
     * FORCED_CHOICE pairs. Each pair map may contain optional keys {@code aAr} and {@code bAr}
     * which are the Arabic translations of {@code a} and {@code b} respectively.
     */
    List<Map<String, Object>> forcedChoicePairs
) {}
