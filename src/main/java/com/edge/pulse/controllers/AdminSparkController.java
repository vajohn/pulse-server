package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.PagedResponse;
import com.edge.pulse.data.dto.spark.*;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.services.spark.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/spark")
@RequiredArgsConstructor
public class AdminSparkController {

    private final SparkService sparkService;
    private final SparkAdminService sparkAdminService;
    private final SparkVoteService voteService;
    private final SparkWinnerService winnerService;
    private final AuditService auditService;

    // ─── Award Period Management ──────────────────────────────────────────────

    @GetMapping("/award-periods")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<PagedResponse<AwardPeriodDto>> listPeriods(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AwardPeriodStatus status) {
        return ResponseEntity.ok(sparkService.getPagedPeriods(page, size, status));
    }

    @PostMapping("/award-periods")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<AwardPeriodDto> createPeriod(
            @RequestBody @Valid CreateAwardPeriodRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        AwardPeriodDto result = sparkAdminService.createPeriod(userId, request);
        auditService.logAction(userId, "SPARK_PERIOD_CREATE", "AWARD_PERIOD", result.id(),
                auditService.buildDetail("name", result.name()), null);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/award-periods/{id}")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<AwardPeriodDto> updatePeriod(
            @PathVariable UUID id,
            @RequestBody @Valid CreateAwardPeriodRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        AwardPeriodDto result = sparkAdminService.updatePeriod(id, request);
        auditService.logAction(userId, "SPARK_PERIOD_UPDATE", "AWARD_PERIOD", id, null, null);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/award-periods/{id}/advance")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<AwardPeriodDto> advancePeriodStatus(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        AwardPeriodDto result = sparkAdminService.advancePeriodStatus(id);
        auditService.logAction(userId, "SPARK_PERIOD_STATUS_ADVANCE", "AWARD_PERIOD", id,
                auditService.buildDetail("newStatus", result.status()), null);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/award-periods/{id}/cancel")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<AwardPeriodDto> cancelPeriod(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        AwardPeriodDto result = sparkAdminService.cancelPeriod(id);
        auditService.logAction(userId, "SPARK_PERIOD_CANCEL", "AWARD_PERIOD", id,
                auditService.buildDetail("newStatus", result.status()), null);
        return ResponseEntity.ok(result);
    }

    // ─── Nominations (HR View — full access) ─────────────────────────────────

    @GetMapping("/nominations")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<List<NominationDto>> getAllNominations(@RequestParam UUID periodId) {
        return ResponseEntity.ok(sparkAdminService.getAllNominations(periodId));
    }

    // ─── Leader Votes (HR View) ───────────────────────────────────────────────

    @GetMapping("/votes")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<List<LeaderVoteDto>> getAllVotes(@RequestParam UUID periodId) {
        return ResponseEntity.ok(voteService.getAllVotesForPeriod(periodId));
    }

    // ─── Winner Finalization ──────────────────────────────────────────────────

    @PostMapping("/winners")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<SparkWinnerDto> finalizeWinner(
            @RequestBody @Valid FinalizeWinnerRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        SparkWinnerDto result = winnerService.finalizeWinner(userId, request);
        auditService.logAction(userId, "SPARK_WINNER_FINALIZE", "SPARK_WINNER", result.id(),
                auditService.buildDetail("category", result.category().id()), null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/winners")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<List<SparkWinnerDto>> getWinners(@RequestParam UUID periodId) {
        return ResponseEntity.ok(winnerService.getWinners(periodId));
    }

    @PostMapping("/winners/announce")
    @PreAuthorize("hasAuthority('SPARK_MANAGE')")
    public ResponseEntity<Void> announceWinners(
            @RequestParam UUID periodId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        winnerService.announceWinners(periodId);
        auditService.logAction(userId, "SPARK_WINNERS_ANNOUNCED", "AWARD_PERIOD", periodId, null, null);
        return ResponseEntity.ok().build();
    }
}
