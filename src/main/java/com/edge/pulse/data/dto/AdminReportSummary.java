package com.edge.pulse.data.dto;

public record AdminReportSummary(
    long totalUsers,
    long activeUsers,
    long totalSurveys,
    long totalAssignments,
    long completedSessions,
    long pendingApprovals
) {}
