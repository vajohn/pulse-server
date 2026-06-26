package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.dto.UpdateQuestionRequest;
import com.edge.pulse.data.dto.psychometric.*;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.mappers.psychometric.TestApprovalMapper;
import com.edge.pulse.services.psychometric.CadenceAdminService;
import com.edge.pulse.services.psychometric.InstrumentService;
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import com.edge.pulse.services.psychometric.TestApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for psychometric test management.
 *
 * <p>Base path: {@code /api/admin/psychometric}
 */
@RestController
@RequestMapping("/api/admin/psychometric")
@RequiredArgsConstructor
public class AdminPsychometricController {

    private final PsychometricAdminService adminService;
    private final CadenceAdminService cadenceAdminService;
    private final InstrumentService instrumentService;
    private final TestApprovalService approvalService;
    private final TestApprovalMapper approvalMapper;

    // ── Micro-engagement cadence config (Phase 3, D2) ─────────────────────────────

    @GetMapping("/tests/{testId}/cadences")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<CadenceConfigDto>> listCadences(@PathVariable UUID testId) {
        return ResponseEntity.ok(cadenceAdminService.list(testId));
    }

    @PostMapping("/tests/{testId}/cadences")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<CadenceConfigDto> createCadence(
            @PathVariable UUID testId, @Valid @RequestBody CadenceConfigRequest req,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cadenceAdminService.create(testId, req, (UUID) auth.getPrincipal()));
    }

    @DeleteMapping("/tests/{testId}/cadences/{cadenceId}")
    @PreAuthorize("hasAuthority('ASSESS_DELETE')")
    public ResponseEntity<Void> deleteCadence(
            @PathVariable UUID testId, @PathVariable UUID cadenceId) {
        cadenceAdminService.deactivate(testId, cadenceId);
        return ResponseEntity.noContent().build();
    }

    // ── Type catalog ─────────────────────────────────────────────────────────────

    @GetMapping("/test-types")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<TestTypeCapabilityDto>> listTestTypes() {
        return ResponseEntity.ok(adminService.listTestTypeCapabilities());
    }

    @GetMapping("/instruments")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<InstrumentDto>> listInstruments() {
        return ResponseEntity.ok(instrumentService.list());
    }

    // ── Test CRUD ────────────────────────────────────────────────────────────────

    @GetMapping("/tests")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<Page<PsychometricTestDto>> listTests(
            @RequestParam(required = false) TestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listTests(status, PageRequest.of(page, Math.min(size, 100))));
    }

    @PostMapping("/tests")
    @PreAuthorize("hasAuthority('ASSESS_CREATE')")
    public ResponseEntity<PsychometricTestDto> createTest(
            @RequestBody @Valid CreatePsychometricTestRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTest(request, userId));
    }

    @GetMapping("/tests/{testId}")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<PsychometricTestDto> getTest(@PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.getTest(testId));
    }

    @PutMapping("/tests/{testId}")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<PsychometricTestDto> updateTest(
            @PathVariable UUID testId,
            @RequestBody UpdatePsychometricTestRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.updateTest(testId, request, userId));
    }

    // ── Approval lifecycle (submit → approve/reject → revise) ────────────────

    /**
     * Submits a DRAFT test for approval: DRAFT → PENDING_APPROVAL.
     * Requires ASSESS_UPDATE. Returns 422 if the test is not scoreable.
     */
    @PostMapping("/tests/{testId}/submit")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<PsychometricTestDto> submit(
            @PathVariable UUID testId, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(approvalService.submitAndGetDto(testId, userId));
    }

    /**
     * Lists approval requests filtered by status (defaults to PENDING).
     * Requires ASSESS_APPROVE.
     */
    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('ASSESS_APPROVE')")
    public ResponseEntity<List<TestApprovalRequestDto>> listApprovals(
            @RequestParam(defaultValue = "PENDING") TestApprovalStatus status) {
        return ResponseEntity.ok(approvalService.listDtos(status));
    }

    /**
     * Reviews a PENDING approval request (APPROVE → ACTIVE; REJECT → DRAFT).
     * Requires ASSESS_APPROVE. Returns 403 if reviewer == submitter.
     */
    @PostMapping("/approvals/{requestId}/review")
    @PreAuthorize("hasAuthority('ASSESS_APPROVE')")
    public ResponseEntity<TestApprovalRequestDto> review(
            @PathVariable UUID requestId,
            @RequestBody @Valid ReviewTestApprovalRequest body,
            Authentication auth) {
        UUID reviewerId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(approvalService.reviewAndGetDto(reviewerId, requestId, body));
    }

    /**
     * Clones an ACTIVE test into a new DRAFT version (version + 1, supersedes = prior).
     * Requires ASSESS_UPDATE. Returns 409 if the test is not ACTIVE.
     */
    @PostMapping("/tests/{testId}/revise")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<PsychometricTestDto> revise(
            @PathVariable UUID testId, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        return ResponseEntity.ok(approvalService.reviseAndGetDto(testId, userId));
    }

    @DeleteMapping("/tests/{testId}")
    @PreAuthorize("hasAuthority('ASSESS_DELETE')")
    public ResponseEntity<Void> archiveTest(
            @PathVariable UUID testId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        adminService.archiveTest(testId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Scale CRUD ────────────────────────────────────────────────────────────────

    @GetMapping("/tests/{testId}/scales")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<PsychometricScaleDto>> listScales(@PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.listScales(testId));
    }

    @PostMapping("/tests/{testId}/scales")
    @PreAuthorize("hasAuthority('ASSESS_CREATE')")
    public ResponseEntity<PsychometricScaleDto> createScale(
            @PathVariable UUID testId,
            @RequestBody @Valid CreateScaleRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createScale(testId, request, userId));
    }

    @PutMapping("/tests/{testId}/scales/{scaleId}")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<PsychometricScaleDto> updateScale(
            @PathVariable UUID testId,
            @PathVariable UUID scaleId,
            @RequestBody UpdateScaleRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.updateScale(testId, scaleId, request, userId));
    }

    @DeleteMapping("/tests/{testId}/scales/{scaleId}")
    @PreAuthorize("hasAuthority('ASSESS_DELETE')")
    public ResponseEntity<Void> deleteScale(
            @PathVariable UUID testId,
            @PathVariable UUID scaleId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        adminService.deleteScale(testId, scaleId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Question CRUD (psychometric-gated) ──────────────────────────────────────

    @GetMapping("/tests/{testId}/questions")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<QuestionDto>> listQuestions(@PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.listQuestions(testId));
    }

    @PostMapping("/tests/{testId}/questions")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<QuestionDto> addQuestion(
            @PathVariable UUID testId,
            @RequestBody @Valid AddQuestionRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.addQuestion(testId, request, userId));
    }

    @PutMapping("/tests/{testId}/questions/{questionId}")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<QuestionDto> updateQuestion(
            @PathVariable UUID testId,
            @PathVariable UUID questionId,
            @RequestBody @Valid UpdateQuestionRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.updateQuestion(testId, questionId, request, userId));
    }

    @DeleteMapping("/tests/{testId}/questions/{questionId}")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID testId,
            @PathVariable UUID questionId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        adminService.deleteQuestion(testId, questionId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── Visibility policy management ─────────────────────────────────────────────

    @GetMapping("/tests/{testId}/visibility-policies")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<List<VisibilityPolicyDto>> listVisibilityPolicies(
            @PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.listVisibilityPolicies(testId));
    }

    @PutMapping("/tests/{testId}/visibility-policy")
    @PreAuthorize("hasAuthority('ASSESS_UPDATE')")
    public ResponseEntity<VisibilityPolicyDto> upsertVisibilityPolicy(
            @PathVariable UUID testId,
            @RequestBody @Valid UpsertVisibilityPolicyRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.upsertVisibilityPolicy(testId, request, userId));
    }

    // ── Test analytics (materialized views) ──────────────────────────────────────

    @GetMapping("/tests/{testId}/analytics")
    @PreAuthorize("hasAuthority('ASSESS_RESULT_READ')")
    public ResponseEntity<PsychometricTestAnalyticsDto> getTestAnalytics(
            @PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.getTestAnalytics(testId));
    }

    // ── HR result detail ─────────────────────────────────────────────────────────

    @GetMapping("/results/{resultId}")
    @PreAuthorize("hasAuthority('ASSESS_RESULT_READ')")
    public ResponseEntity<CandidateTestResultDetailsDto> getResultDetail(
            @PathVariable UUID resultId) {
        return ResponseEntity.ok(adminService.getAdminResultDetail(resultId));
    }

    // ── Result management ────────────────────────────────────────────────────────

    @GetMapping("/tests/{testId}/results")
    @PreAuthorize("hasAuthority('ASSESS_RESULT_READ')")
    public ResponseEntity<Page<TestResultSummaryDto>> listResults(
            @PathVariable UUID testId,
            @RequestParam(required = false) TestResultStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.listResults(testId, status, page, size));
    }

    @PostMapping("/tests/{testId}/results/{resultId}/review")
    @PreAuthorize("hasAuthority('ASSESS_RESULT_READ')")
    public ResponseEntity<TestResultSummaryDto> reviewResult(
            @PathVariable UUID testId,
            @PathVariable UUID resultId,
            @RequestBody @Valid ReviewResultRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.reviewResult(resultId, userId, request.status(), request.notes()));
    }

    @PostMapping("/tests/{testId}/results/{resultId}/rescore")
    @PreAuthorize("hasAuthority('ASSESS_KEY_MANAGE')")
    public ResponseEntity<Void> rescoreResult(
            @PathVariable UUID testId,
            @PathVariable UUID resultId,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        adminService.rescoreResult(resultId, userId);
        return ResponseEntity.noContent().build();
    }

    // ── UI-driven Scoring Key ─────────────────────────────────────────────────

    @GetMapping("/tests/{testId}/scoring-key")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<ScoringKeyItemDto>> getScoringKey(@PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.getScoringKey(testId));
    }

    @PutMapping("/tests/{testId}/scoring-key")
    @PreAuthorize("hasAuthority('ASSESS_KEY_MANAGE')")
    public ResponseEntity<List<ScoringKeyItemDto>> saveScoringKey(
            @PathVariable UUID testId,
            @RequestBody @Valid List<ScoringKeyItemRequest> items,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.saveScoringKey(testId, items, userId));
    }

    // ── UI-driven Norm Table ──────────────────────────────────────────────────

    @GetMapping("/tests/{testId}/norm-table")
    @PreAuthorize("hasAuthority('ASSESS_READ')")
    public ResponseEntity<List<NormEntryDto>> getNormTable(@PathVariable UUID testId) {
        return ResponseEntity.ok(adminService.getNormTable(testId));
    }

    @PutMapping("/tests/{testId}/norm-table")
    @PreAuthorize("hasAuthority('ASSESS_KEY_MANAGE')")
    public ResponseEntity<List<NormEntryDto>> saveNormTable(
            @PathVariable UUID testId,
            @RequestBody @Valid List<NormEntryRequest> entries,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(adminService.saveNormTable(testId, entries, userId));
    }
}
