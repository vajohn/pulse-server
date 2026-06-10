package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.services.EntraDirectorySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/entra")
@RequiredArgsConstructor
public class AdminEntraController {

    private final EntraDirectorySyncService syncService;

    /**
     * Triggers a manual directory sync and returns the result immediately.
     * Requires USER_UPDATE authority (HR_FULL_CRUD or equivalent).
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('SYNC_TRIGGER')")
    public ResponseEntity<DirectorySyncResultDto> triggerSync() {
        DirectorySyncResultDto result = syncService.syncDirectory();
        return ResponseEntity.ok(result);
    }

    /**
     * Returns sync status: total active users, stale user count, and last sync timestamp.
     * Requires USER_READ authority.
     */
    @GetMapping("/sync/status")
    @PreAuthorize("hasAuthority('SYNC_STATUS')")
    public ResponseEntity<DirectorySyncResultDto> getSyncStatus() {
        return ResponseEntity.ok(syncService.getStatus());
    }
}
