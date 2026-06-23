package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.EngagementSummaryDto;
import com.edge.pulse.services.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Org-wide engagement analytics for the web HR dashboard (PULSE-WEB-4).
 *
 * <p>Consumed by WEB-5 (dashboard) and WEB-6 (org-scope switcher).
 *
 * <p>Authorization: gated by {@code REPORT_VIEW}. No dedicated {@code ANALYTICS_*}
 * permission exists in the taxonomy, so the established analytics gate
 * ({@code REPORT_VIEW} — the same one used by {@code /api/analytics/dashboard}
 * and {@code /api/admin/reports/**}) is reused. The caller is additionally
 * bounded to their own org scope inside the service via SCOPE_* resolution.
 */
@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@Validated
public class WebAnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * Engagement summary for an org scope + rolling period.
     *
     * @param scopeLevel optional level label (GROUP|CLUSTER|ENTITY|ORG_UNIT|TEAM); echoed back, informational
     * @param nodeId     optional org unit id to scope to; omitted = global (broad scope) or own subtree (narrow scope)
     * @param includeChildren include the node's descendants (default true); false = the node alone
     * @param days       rolling engagement window in days (default 30, min 1, max 730 / ~2 years).
     *                   I-4: bounded by {@code @Max(730)} so a caller cannot request an
     *                   unbounded historical scan; the service additionally clamps defensively.
     */
    @GetMapping("/engagement")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<EngagementSummaryDto> getEngagement(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String scopeLevel,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(defaultValue = "true") boolean includeChildren,
            @RequestParam(defaultValue = "30") @Min(1) @Max(730) int days) {
        return ResponseEntity.ok(
                analyticsService.getEngagementSummary(scopeLevel, nodeId, includeChildren, days, userId));
    }
}
