package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.spark.*;
import com.edge.pulse.services.spark.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spark")
@RequiredArgsConstructor
@Validated
public class SparkController {

    private final SparkService sparkService;
    private final SparkNominationService nominationService;
    private final SparkVoteService voteService;
    private final SparkWinnerService winnerService;

    // ─── Home ────────────────────────────────────────────────────────────────

    @GetMapping("/home")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SparkHomeDto> getHome(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(sparkService.getHome(userId, sparkService.isLeader(auth)));
    }

    // ─── Award Periods ───────────────────────────────────────────────────────

    @GetMapping("/award-periods")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AwardPeriodDto>> getPeriods() {
        return ResponseEntity.ok(sparkService.getAllPeriods());
    }

    @GetMapping("/award-periods/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AwardPeriodDto> getCurrentPeriod() {
        return sparkService.getCurrentPeriod()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // ─── Categories ──────────────────────────────────────────────────────────

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SparkCategoryDto>> getCategories() {
        return ResponseEntity.ok(sparkService.getActiveCategories());
    }

    // ─── Nominations ─────────────────────────────────────────────────────────

    @PostMapping("/nominations")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<NominationDto> submitNomination(
            @RequestBody @Valid SubmitNominationRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(nominationService.submit(userId, request));
    }

    @GetMapping("/nominations/my")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<List<NominationDto>> getMyNominations(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(nominationService.getMyNominations(userId));
    }

    @GetMapping("/nominations/entity")
    @PreAuthorize("hasAuthority('SPARK_VOTE')")
    public ResponseEntity<List<NominationDto>> getVotingNominations(
            @RequestParam UUID periodId,
            @RequestParam String categoryId) {
        return ResponseEntity.ok(nominationService.getVotingNominations(periodId, categoryId));
    }

    @GetMapping("/nominations/{id}")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<NominationDto> getNomination(@PathVariable UUID id) {
        return ResponseEntity.ok(nominationService.getNomination(id));
    }

    @PostMapping("/nominations/{id}/attachments")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<NominationDto> addAttachment(
            @PathVariable UUID id,
            @RequestBody @Valid AddAttachmentRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(nominationService.addAttachment(id, userId, request));
    }

    @DeleteMapping("/nominations/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        nominationService.deleteAttachment(id, attachmentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ─── Leader Voting ────────────────────────────────────────────────────────

    @PostMapping("/votes")
    @PreAuthorize("hasAuthority('SPARK_VOTE')")
    public ResponseEntity<LeaderVoteDto> castVote(
            @RequestBody @Valid CastVoteRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(voteService.castVote(userId, request));
    }

    @GetMapping("/votes/my")
    @PreAuthorize("hasAuthority('SPARK_VOTE')")
    public ResponseEntity<List<LeaderVoteDto>> getMyVotes(
            @RequestParam UUID periodId,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(voteService.getMyVotes(userId, periodId));
    }

    // ─── Winners ──────────────────────────────────────────────────────────────

    @GetMapping("/winners")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SparkWinnerDto>> getWinners(@RequestParam UUID periodId) {
        return ResponseEntity.ok(winnerService.getWinners(periodId));
    }

    @PostMapping("/winners/{id}/congratulate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SparkCongratulationDto> congratulate(
            @PathVariable UUID id,
            @RequestBody @Valid CongratulateRequest request,
            Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(winnerService.congratulate(id, userId, request));
    }

    @GetMapping("/winners/{id}/congratulations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<SparkCongratulationDto>> getCongratulations(@PathVariable UUID id) {
        return ResponseEntity.ok(winnerService.getCongratulations(id));
    }

    // ─── Employee Search ──────────────────────────────────────────────────────

    @GetMapping("/employees/search")
    @PreAuthorize("hasAuthority('SPARK_NOMINATE')")
    public ResponseEntity<List<NomineeInfoDto>> searchEmployees(
            @RequestParam @Size(min = 3, max = 50, message = "Search query must be between 3 and 50 characters") String q) {
        return ResponseEntity.ok(sparkService.searchEmployees(q));
    }
}
