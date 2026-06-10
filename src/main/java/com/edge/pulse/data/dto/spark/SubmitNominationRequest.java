package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SubmitNominationRequest(
        @NotNull UUID awardPeriodId,
        @NotBlank String categoryId,
        @NotNull UUID nomineeId,
        @NotBlank @Size(min = 100, max = 2000) String justification
) {}
