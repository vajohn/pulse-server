package com.edge.pulse.data.dto;

import java.time.LocalDateTime;

public record DirectorySyncResultDto(
    int usersProcessed,
    int usersCreated,
    int usersUpdated,
    int usersDeactivated,
    int orgUnitsProcessed,
    int orgUnitsCreated,
    int orgUnitsUpdated,
    int errors,
    LocalDateTime syncedAt
) {}
