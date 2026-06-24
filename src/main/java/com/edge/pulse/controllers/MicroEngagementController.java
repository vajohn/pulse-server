package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CheckInDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.services.psychometric.micro.MicroEngagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Candidate-facing pull-delivery endpoints for micro-engagement check-ins (Phase 3, D1).
 *
 * <p>Base path: {@code /api/psychometric/check-ins}
 */
@RestController
@RequestMapping("/api/psychometric/check-ins")
@RequiredArgsConstructor
public class MicroEngagementController {

    private final MicroEngagementService service;

    /** Pull-delivery inbox: micro-sets available to the candidate now (D1). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CheckInDto>> listCheckIns(Authentication auth) {
        return ResponseEntity.ok(service.listCheckIns((UUID) auth.getPrincipal()));
    }

    /** Starts a sampled micro-session for the chosen cadence; returns the question payload. */
    @PostMapping("/{cadenceId}/session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PsychometricSessionDto> startCheckIn(
            @PathVariable UUID cadenceId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.buildCheckInSession(cadenceId, (UUID) auth.getPrincipal()));
    }
}
