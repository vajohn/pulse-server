package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.Size;

public record CongratulateRequest(
        String reaction,
        @Size(max = 500) String message
) {}
