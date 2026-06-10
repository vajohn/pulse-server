package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.BatchSubmitRequest;
import com.edge.pulse.data.dto.psychometric.CandidateTestDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.dto.psychometric.HeartbeatResponse;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.dto.psychometric.StartPsychometricSessionRequest;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.services.psychometric.CandidatePsychometricService;
import com.edge.pulse.services.psychometric.PsychometricSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/psychometric")
@RequiredArgsConstructor
public class CandidatePsychometricController {

    private final CandidatePsychometricService service;
    private final PsychometricSessionService sessionService;

    // ── Test metadata (pre-test gate) ─────────────────────────────────────────

    @GetMapping("/tests/{testId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CandidateTestDto> getTestDetails(
            @PathVariable UUID testId, Authentication auth) {
        return ResponseEntity.ok(service.getTestDetails(testId, (UUID) auth.getPrincipal()));
    }

    // ── Results ───────────────────────────────────────────────────────────────

    @GetMapping("/my-results")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CandidateTestResultDto>> getMyResults(Authentication auth) {
        return ResponseEntity.ok(service.getMyResults((UUID) auth.getPrincipal()));
    }

    @GetMapping("/results/{resultId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CandidateTestResultDetailsDto> getResultDetail(
            @PathVariable UUID resultId, Authentication auth) {
        return ResponseEntity.ok(service.getResultDetail(resultId, (UUID) auth.getPrincipal()));
    }

    /**
     * Manager view: list of all team results within the caller's org-unit subtree.
     * Returns up to 200 most-recent results across all tests.
     */
    @GetMapping("/my-team/results")
    @PreAuthorize("hasAnyAuthority('REPORT_ASSESS_VIEW', 'REPORT_ALL', 'ASSESS_ALL')")
    public ResponseEntity<List<TestResultSummaryDto>> getTeamResults(Authentication auth) {
        return ResponseEntity.ok(service.getTeamResults((UUID) auth.getPrincipal()));
    }

    /**
     * Manager view: returns a team member's result detail using the MANAGER visibility policy.
     * Access is limited to results whose subject falls within the caller's org-unit subtree.
     */
    @GetMapping("/my-team/results/{resultId}")
    @PreAuthorize("hasAnyAuthority('REPORT_ASSESS_VIEW', 'REPORT_ALL', 'ASSESS_ALL')")
    public ResponseEntity<CandidateTestResultDetailsDto> getTeamResultDetail(
            @PathVariable UUID resultId, Authentication auth) {
        return ResponseEntity.ok(service.getTeamResultDetail(resultId, (UUID) auth.getPrincipal()));
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Opens or resumes a psychometric session for the given survey.
     * Returns the full question payload in randomised order.
     *
     * <p>For resumed sessions, the original {@code serverStartEpoch} and
     * {@code itemSequence} are preserved so the timer is not reset.
     */
    @PostMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PsychometricSessionDto> startSession(
            @Valid @RequestBody StartPsychometricSessionRequest request,
            Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.startSession(request.surveyId(), (UUID) auth.getPrincipal()));
    }

    /**
     * Returns remaining seconds for the session timer.
     * Returns {@code null} for untimed sessions.
     * Called every 30 s by the Flutter client to resync the local countdown.
     */
    @GetMapping("/sessions/{id}/time")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<HeartbeatResponse> getSessionTime(
            @PathVariable UUID id, Authentication auth) {
        return ResponseEntity.ok(sessionService.getHeartbeat(id, (UUID) auth.getPrincipal()));
    }

    /**
     * Records an app-background (focus-loss) event.
     * Called by the Flutter client whenever {@code AppLifecycleState.paused} fires
     * during an active session.
     */
    @PostMapping("/sessions/{id}/focus-event")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logFocusEvent(
            @PathVariable UUID id, Authentication auth) {
        sessionService.logFocusEvent(id, (UUID) auth.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    /**
     * Batch-submits all candidate answers, marks the session complete,
     * and triggers psychometric scoring.
     *
     * <p>Returns the {@link CandidateTestResultDto} immediately — scoring is synchronous.
     * Status will be {@code SCORED} if an active scoring key exists, or {@code PENDING}
     * if the key has not yet been published.
     */
    @PostMapping("/sessions/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CandidateTestResultDto> completeSession(
            @PathVariable UUID id,
            @Valid @RequestBody BatchSubmitRequest request,
            Authentication auth) {
        return ResponseEntity.ok(
                sessionService.completeSession(id, (UUID) auth.getPrincipal(), request));
    }
}
