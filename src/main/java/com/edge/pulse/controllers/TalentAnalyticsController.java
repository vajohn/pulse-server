package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CapabilityTrendDto;
import com.edge.pulse.data.dto.psychometric.CohortAnalyticsDto;
import com.edge.pulse.services.psychometric.TalentAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/psychometric/analytics")
@RequiredArgsConstructor
public class TalentAnalyticsController {

    private final TalentAnalyticsService service;

    /** Org-scoped cohort distribution per leaf scale (k-anon masked, excludes restricted/invalid). */
    @GetMapping("/tests/{testId}/cohort")
    @PreAuthorize("hasAnyAuthority('REPORT_ASSESS_VIEW', 'REPORT_ALL', 'ASSESS_ALL')")
    public ResponseEntity<CohortAnalyticsDto> cohort(
            @PathVariable UUID testId, Authentication auth) {
        return ResponseEntity.ok(service.cohortAnalytics(testId, (UUID) auth.getPrincipal()));
    }

    /** Per-employee capability trend (history series + norm-boundary flag). */
    @GetMapping("/tests/{testId}/users/{userId}/trend")
    @PreAuthorize("hasAnyAuthority('ASSESS_RESULT_READ', 'ASSESS_ALL')")
    public ResponseEntity<CapabilityTrendDto> trend(
            @PathVariable UUID testId, @PathVariable UUID userId, Authentication auth) {
        return ResponseEntity.ok(
                service.capabilityTrend((UUID) auth.getPrincipal(), userId, testId));
    }
}
