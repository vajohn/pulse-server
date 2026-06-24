package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.Cadence;
import java.time.LocalDateTime;
import java.util.UUID;

public record CadenceConfigDto(
        UUID id, UUID testId, Cadence cadence, int maxItemsPerAdmin,
        UUID orgUnitId, boolean includeChildren,
        LocalDateTime startsAt, LocalDateTime endsAt, boolean active) {}
