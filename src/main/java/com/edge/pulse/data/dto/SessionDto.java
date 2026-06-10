package com.edge.pulse.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessionDto(
    UUID id,
    UUID formId,
    @JsonProperty("isAnonymous") boolean isAnonymous,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    List<AnswerDto> currentAnswers
) {}
