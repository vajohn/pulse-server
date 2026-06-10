package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AdminReportSummary;
import com.edge.pulse.data.dto.SurveyReportDto;
import com.edge.pulse.services.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {
    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<AdminReportSummary> getSummary() {
        return ResponseEntity.ok(analyticsService.getAdminReportSummary());
    }

    @GetMapping("/form/{id}")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<SurveyReportDto> getSurveyReport(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID orgUnitId,
            @AuthenticationPrincipal UUID userId,
            Authentication authentication) {
        boolean canViewText = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "REPORT_TEXT_VIEW".equals(a.getAuthority()));
        return ResponseEntity.ok(analyticsService.getSurveyReport(id, orgUnitId, userId, canViewText));
    }

    @GetMapping("/export/{id}")
    @PreAuthorize("hasAuthority('REPORT_EXPORT')")
    public ResponseEntity<String> exportReport(@PathVariable UUID id) {
        // Placeholder — CSV/PDF export will be implemented in a follow-up
        return ResponseEntity.ok("Export not yet implemented for form: " + id);
    }
}
