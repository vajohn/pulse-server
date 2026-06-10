package com.edge.pulse.data.dto.spark;

import java.util.UUID;

public record NomineeInfoDto(
        UUID id,
        String displayName,
        String email,
        String title,
        String orgUnit
) {}
