package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.QuestionType;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record UpdateQuestionRequest(
    @NotBlank String body,
    /** Arabic translation of body. Null = leave existing translation unchanged. */
    String bodyAr,
    QuestionType questionType,
    int displayOrder,
    LocalDateTime expirationDate,
    Integer scaleMin,
    Integer scaleMax,
    String minLabel,
    /** Arabic translation of minLabel. Null = leave existing unchanged. */
    String minLabelAr,
    String maxLabel,
    /** Arabic translation of maxLabel. Null = leave existing unchanged. */
    String maxLabelAr,
    /**
     * FORCED_CHOICE pairs. Each pair map may contain optional keys {@code aAr} and {@code bAr}.
     */
    List<Map<String, Object>> forcedChoicePairs,
    List<String> subjectLabels,
    /** Arabic translations of subjectLabels (positional parallel). Null = leave existing unchanged. */
    List<String> subjectLabelsAr
) {}
