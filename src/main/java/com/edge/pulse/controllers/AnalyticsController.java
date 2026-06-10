package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AnalyticsSummaryDto;
import com.edge.pulse.data.dto.OrgUnitNodeDto;
import com.edge.pulse.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Employee-facing team analytics.
     * Any authenticated user can see their team's aggregate data;
     * data is withheld (thresholdMet=false) when fewer than 5 respondents exist.
     *
     * @param orgUnitId optional org unit filter (HR only; managers are always scoped to their own unit)
     * @param days      rolling window in days (default 30)
     */
    @GetMapping("/team")
    @PreAuthorize("hasAnyAuthority('SCOPE_TEAM', 'SCOPE_ORG_WIDE', 'SCOPE_ENTITY')")
    public ResponseEntity<AnalyticsSummaryDto> getTeamAnalytics(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getTeamAnalytics(orgUnitId, userId, days));
    }

    /**
     * HR/admin dashboard analytics (same data, REPORT_VIEW permission required).
     *
     * @param orgUnitId optional org unit filter
     * @param days      rolling window in days (default 30)
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<AnalyticsSummaryDto> getDashboard(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) UUID orgUnitId,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getTeamAnalytics(orgUnitId, userId, days));
    }

    /**
     * Returns org units visible to the requesting user for report filtering.
     * HR sees all; MANAGER sees their subtree only.
     */
    @GetMapping("/org-units")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<List<OrgUnitNodeDto>> getVisibleOrgUnits(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(analyticsService.getVisibleOrgUnits(userId));
    }
}
