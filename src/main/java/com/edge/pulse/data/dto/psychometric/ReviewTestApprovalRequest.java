package com.edge.pulse.data.dto.psychometric;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/admin/psychometric/approvals/{requestId}/review}.
 *
 * @param decision         APPROVE or REJECT (validated in service)
 * @param approvalReference Off-system reference (e.g. email date) — optional, used for APPROVE
 * @param comment          Required for REJECT; optional for APPROVE
 */
public record ReviewTestApprovalRequest(
        @NotBlank String decision,
        String approvalReference,
        String comment
) {}
