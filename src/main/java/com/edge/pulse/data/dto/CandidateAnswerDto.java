package com.edge.pulse.data.dto;
import java.util.UUID;

public record CandidateAnswerDto(
    UUID id,
    String label,
    /** Arabic translation of {@link #label}. Null when not yet translated. */
    String labelAr,
    int displayOrder
) {}
