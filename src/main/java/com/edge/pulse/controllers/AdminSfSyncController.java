package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SfDirectorySyncService;
import com.edge.pulse.services.SfUserExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

import java.util.UUID;

/**
 * Admin endpoints to manually trigger and monitor SAP SuccessFactors directory sync.
 */
@RestController
@RequestMapping("/api/admin/sf")
@RequiredArgsConstructor
public class AdminSfSyncController {

    private final SfDirectorySyncService syncService;
    private final SfUserExportService exportService;
    private final AuditService auditService;

    /**
     * Triggers a full SF sync (all users + org tree rebuild).
     * Long-running — consider making async for production.
     */
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('SYNC_TRIGGER')")
    public ResponseEntity<DirectorySyncResultDto> triggerFullSync(Authentication auth) {
        UUID callerId = (UUID) auth.getPrincipal();
        DirectorySyncResultDto result = syncService.fullSync();
        auditService.logAction(callerId, "SF_FULL_SYNC", "SF_SYNC", null,
                auditService.buildDetail("usersProcessed", result.usersProcessed(),
                        "orgUnitsProcessed", result.orgUnitsProcessed(),
                        "errors", result.errors()), null);
        return ResponseEntity.ok(result);
    }

    /**
     * Triggers an incremental delta sync using the stored OData delta token.
     * Falls back to a full sync if no delta token is available.
     */
    @PostMapping("/sync/delta")
    @PreAuthorize("hasAuthority('SYNC_TRIGGER')")
    public ResponseEntity<DirectorySyncResultDto> triggerDeltaSync(Authentication auth) {
        UUID callerId = (UUID) auth.getPrincipal();
        DirectorySyncResultDto result = syncService.deltaSync();
        auditService.logAction(callerId, "SF_DELTA_SYNC", "SF_SYNC", null,
                auditService.buildDetail("usersProcessed", result.usersProcessed(),
                        "errors", result.errors()), null);
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the status of the most recent SF sync run.
     */
    @GetMapping("/sync/status")
    @PreAuthorize("hasAuthority('SYNC_STATUS')")
    public ResponseEntity<DirectorySyncResultDto> getSyncStatus() {
        return ResponseEntity.ok(syncService.getStatus());
    }

    /**
     * Exports SF users as a CSV file (live OData call to SuccessFactors).
     *
     * <p>Optional {@code fields} query param accepts a comma-separated list of column names.
     * Supported: firstName, lastName, email, status, title, department, division, employeeType, function.
     * Defaults to: firstName, lastName, email.
     *
     * <p>Open endpoint — no auth required (testing only).
     */
    @GetMapping(value = "/users/export", produces = "text/csv")
    public ResponseEntity<String> exportUsersCsv(
            @RequestParam(required = false) String fields) {
        List<String> fieldList = (fields != null && !fields.isBlank())
                ? Arrays.asList(fields.split(","))
                : List.of();
        String csv = exportService.buildCsv(fieldList);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sf-users.csv\"")
                .body(csv);
    }

    /**
     * Exports users as a CSV file from the local database (no SF call).
     *
     * <p>Optional {@code fields} query param accepts a comma-separated list of column names.
     * Supported: firstName, lastName, middleName, email, department, division, status.
     * Defaults to: firstName, lastName, email.
     *
     * <p>firstName/lastName/middleName are derived by splitting displayName on whitespace.
     *
     * <p>Open endpoint — no auth required (testing only).
     */
    @GetMapping(value = "/users/export/db", produces = "text/csv")
    public ResponseEntity<String> exportUsersCsvFromDb(
            @RequestParam(required = false) String fields) {
        List<String> fieldList = (fields != null && !fields.isBlank())
                ? Arrays.asList(fields.split(","))
                : List.of();
        String csv = exportService.buildCsvFromDb(fieldList);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sf-users-db.csv\"")
                .body(csv);
    }
}
