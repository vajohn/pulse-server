package com.edge.pulse.data.dto;
import java.util.List;
import java.util.UUID;

public record FormDto(
    UUID id,
    String title,
    String description,
    int anonWindowMinutes,
    List<QuestionDto> questions,
    String formType
) {}
