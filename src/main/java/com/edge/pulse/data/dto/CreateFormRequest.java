package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateFormRequest(
    @NotBlank String title,
    String description,
    Integer anonWindowMinutes
) {}
