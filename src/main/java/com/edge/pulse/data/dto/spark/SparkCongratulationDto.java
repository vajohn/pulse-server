package com.edge.pulse.data.dto.spark;

import java.time.LocalDateTime;
import java.util.UUID;

public record SparkCongratulationDto(
        UUID id,
        UUID userId,
        String userName,
        String reaction,
        String message,
        LocalDateTime createdAt
) {}
