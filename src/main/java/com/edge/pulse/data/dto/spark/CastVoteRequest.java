package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CastVoteRequest(
        @NotNull UUID awardPeriodId,
        @NotBlank String categoryId,
        @NotNull UUID nominationId,
        @Size(max = 500) String endorsementComment
) {}
