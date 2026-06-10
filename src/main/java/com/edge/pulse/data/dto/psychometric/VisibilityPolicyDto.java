package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.ResultAudience;

import java.util.UUID;

public record VisibilityPolicyDto(
        UUID id,
        ResultAudience audience,
        boolean showRawScore,
        boolean showStenProfile,
        boolean showPercentile,
        boolean showScaleBreakdown,
        boolean showCompetencyMap,
        boolean showPassFailOnly
) {}
