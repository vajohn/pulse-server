package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.ResultAudience;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/admin/psychometric/tests/{testId}/visibility-policy}.
 *
 * <p>Sets all visibility flags for one audience (CANDIDATE, MANAGER, or HR_ADMIN).
 * The operation is an upsert — creates the policy row if absent, updates it if present.
 */
public record UpsertVisibilityPolicyRequest(
        @NotNull ResultAudience audience,
        boolean showRawScore,
        boolean showStenProfile,
        boolean showPercentile,
        boolean showScaleBreakdown,
        boolean showCompetencyMap,
        boolean showPassFailOnly
) {}
