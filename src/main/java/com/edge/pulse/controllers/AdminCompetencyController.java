package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CompetencyAdminDto;
import com.edge.pulse.data.dto.psychometric.CompetencyWeightDto;
import com.edge.pulse.data.dto.psychometric.CreateCompetencyRequest;
import com.edge.pulse.data.dto.psychometric.UpdateCompetencyRequest;
import com.edge.pulse.data.dto.psychometric.UpsertCompetencyWeightRequest;
import com.edge.pulse.services.psychometric.CompetencyAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for UAE military competency framework management.
 *
 * <p>All endpoints require {@code ASSESS_COMPETENCY_MANAGE} authority.
 *
 * <p>Base path: {@code /api/admin/psychometric/competencies}
 */
@RestController
@RequestMapping("/api/admin/psychometric/competencies")
@RequiredArgsConstructor
public class AdminCompetencyController {

    private final CompetencyAdminService competencyService;

    @GetMapping
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<List<CompetencyAdminDto>> listCompetencies() {
        return ResponseEntity.ok(competencyService.listCompetencies());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<CompetencyAdminDto> createCompetency(
            @RequestBody @Valid CreateCompetencyRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(competencyService.createCompetency(request, userId));
    }

    @PutMapping("/{competencyId}")
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<CompetencyAdminDto> updateCompetency(
            @PathVariable UUID competencyId,
            @RequestBody @Valid UpdateCompetencyRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(competencyService.updateCompetency(competencyId, request, userId));
    }

    @DeleteMapping("/{competencyId}")
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<Void> deleteCompetency(
            @PathVariable UUID competencyId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        competencyService.deleteCompetency(competencyId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{competencyId}/weights")
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<List<CompetencyWeightDto>> listWeights(
            @PathVariable UUID competencyId) {
        return ResponseEntity.ok(competencyService.listWeights(competencyId));
    }

    @PutMapping("/{competencyId}/scales/{scaleId}")
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<CompetencyWeightDto> upsertWeight(
            @PathVariable UUID competencyId,
            @PathVariable UUID scaleId,
            @RequestBody @Valid UpsertCompetencyWeightRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(competencyService.upsertWeight(competencyId, scaleId, request, userId));
    }

    @DeleteMapping("/{competencyId}/scales/{scaleId}")
    @PreAuthorize("hasAuthority('ASSESS_COMPETENCY_MANAGE')")
    public ResponseEntity<Void> deleteWeight(
            @PathVariable UUID competencyId,
            @PathVariable UUID scaleId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        competencyService.deleteWeight(competencyId, scaleId, userId);
        return ResponseEntity.noContent().build();
    }
}
