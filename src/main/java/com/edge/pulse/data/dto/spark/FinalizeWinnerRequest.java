package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record FinalizeWinnerRequest(
        @NotNull UUID awardPeriodId,
        @NotBlank String categoryId,
        @NotNull UUID winnerId,
        String hrJustification
) {}
