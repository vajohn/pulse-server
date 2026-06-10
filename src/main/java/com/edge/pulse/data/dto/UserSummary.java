package com.edge.pulse.data.dto;

import java.util.List;
import java.util.UUID;

public record UserSummary(
    UUID id,
    String email,
    String displayName,
    String department,
    List<String> roles,
    List<String> permissions,
    UUID orgUnitId,
    String orgUnitName
) {}
