package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.Cadence;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;

public record CadenceConfigRequest(
        @NotNull Cadence cadence,
        @Min(1) @Max(15) int maxItemsPerAdmin,
        UUID orgUnitId,                 // null = whole org
        boolean includeChildren,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {}
