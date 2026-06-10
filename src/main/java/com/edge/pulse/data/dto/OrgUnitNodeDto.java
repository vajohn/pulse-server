package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.OrgLevel;

import java.util.UUID;

public record OrgUnitNodeDto(
        UUID id,
        String orgUnitName,
        OrgLevel orgLevel,
        UUID parentId,
        int depth,
        String path
) {}
