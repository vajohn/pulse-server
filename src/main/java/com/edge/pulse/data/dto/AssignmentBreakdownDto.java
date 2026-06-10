package com.edge.pulse.data.dto;

import java.util.UUID;

public record AssignmentBreakdownDto(
        UUID assignmentId,
        String orgUnitName,
        String orgUnitId,           // nullable — used as orgUnitId param when tapped
        long eligibleUsers,
        long completedSessions,
        long inProgressSessions,
        double completionRate,
        boolean privacyThresholdMet
) {}
