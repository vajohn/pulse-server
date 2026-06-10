package com.edge.pulse.data.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchSubmitRequest(
    @NotNull @Size(min = 1) List<@Valid SubmitAnswerRequest> answers
) {}
