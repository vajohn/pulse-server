package com.edge.pulse.data.dto.psychometric;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Read DTO for a {@code TestApprovalRequest}.
 * Returned by the approvals list endpoint and embedded in {@link PsychometricTestDto} as the pending-request summary.
 */
public record TestApprovalRequestDto(
        UUID id,
        UUID testId,
        String testName,
        int testVersion,
        UUID submittedById,
        String submittedByName,
        LocalDateTime submittedAt,
        /** TestApprovalStatus.name() — PENDING, APPROVED, REJECTED */
        String status,
        UUID reviewedById,
        String reviewedByName,
        LocalDateTime reviewedAt,
        String approvalReference,
        String reviewComment
) {}
