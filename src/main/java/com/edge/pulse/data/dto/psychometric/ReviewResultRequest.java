package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.TestResultStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewResultRequest(
        @NotNull TestResultStatus status,   // REVIEWED or FLAGGED
        String notes
) {}
