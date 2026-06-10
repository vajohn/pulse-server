package com.edge.pulse.data.dto;
import com.edge.pulse.data.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuestionDto(
    UUID id,
    String body,
    /** Arabic translation of body. Null when not yet translated. */
    @JsonInclude(JsonInclude.Include.NON_NULL) String bodyAr,
    QuestionType questionType,
    int displayOrder,
    LocalDateTime effectiveDate,
    LocalDateTime expirationDate,
    List<CandidateAnswerDto> candidateAnswers,
    List<String> subjectLabels,
    /** Arabic translations of subjectLabels (positional parallel array). Null when not yet translated. */
    @JsonInclude(JsonInclude.Include.NON_NULL) List<String> subjectLabelsAr,
    boolean archived,
    Integer scaleMin,
    Integer scaleMax,
    String minLabel,
    /** Arabic translation of minLabel. Null when not yet translated. */
    @JsonInclude(JsonInclude.Include.NON_NULL) String minLabelAr,
    String maxLabel,
    /** Arabic translation of maxLabel. Null when not yet translated. */
    @JsonInclude(JsonInclude.Include.NON_NULL) String maxLabelAr,
    @JsonInclude(JsonInclude.Include.NON_NULL) List<Map<String, Object>> forcedChoicePairs
) {}
