package com.edge.pulse.data.dto;

import java.util.List;
import java.util.UUID;

public record SurveyReportDto(
        UUID surveyId,
        String surveyTitle,
        long totalAssignments,
        long totalEligibleUsers,
        long completedSessions,
        long inProgressSessions,
        double completionRate,
        boolean privacyThresholdMet,
        List<QuestionReportDto> questionBreakdowns,
        long anonymousSessions,
        long identifiedSessions,
        List<AssignmentBreakdownDto> assignmentBreakdowns
) {}
