package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.OrgLevel;

import java.util.List;
import java.util.UUID;

public record OrgTreeNodeDto(
    UUID id,
    String orgUnitName,
    String orgUnitCode,
    OrgLevel orgLevel,
    int depth,
    List<OrgTreeNodeDto> children
) {}
