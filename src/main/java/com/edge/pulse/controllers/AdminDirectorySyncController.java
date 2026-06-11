package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SafReconDirectorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Admin endpoints to trigger and monitor the saf-recon directory sync. */
@RestController
@RequestMapping("/api/admin/saf")
@RequiredArgsConstructor
public class AdminDirectorySyncController {

    private final SafReconDirectorySyncService syncService;
    private final AuditService auditService;

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('SYNC_TRIGGER')")
    public ResponseEntity<DirectorySyncResultDto> triggerFullSync(Authentication auth) {
        UUID callerId = (UUID) auth.getPrincipal();
        DirectorySyncResultDto result = syncService.fullSync();
        auditService.logAction(callerId, "SAF_RECON_FULL_SYNC", "SAF_RECON_SYNC", null,
                auditService.buildDetail("usersProcessed", result.usersProcessed(),
                        "orgUnitsProcessed", result.orgUnitsProcessed(),
                        "errors", result.errors()), null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sync/status")
    @PreAuthorize("hasAuthority('SYNC_STATUS')")
    public ResponseEntity<DirectorySyncResultDto> getSyncStatus() {
        return ResponseEntity.ok(syncService.getStatus());
    }
}
