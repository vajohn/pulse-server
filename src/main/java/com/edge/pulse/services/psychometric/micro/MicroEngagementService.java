package com.edge.pulse.services.psychometric.micro;

import com.edge.pulse.data.dto.psychometric.CheckInDto;
import com.edge.pulse.data.dto.psychometric.PsychometricQuestionDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.enums.ResultMode;
import com.edge.pulse.data.enums.ScaleProgressState;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.AssessmentCadence;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ScaleProgress;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.models.psychometric.UserItemExposure;
import com.edge.pulse.data.models.psychometric.UserItemExposureId;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.AssessmentCadenceRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.ScaleProgressRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.UserItemExposureRepository;
import com.edge.pulse.services.psychometric.PsychometricSessionService;
import com.edge.pulse.services.psychometric.micro.model.SamplerInput;
import com.edge.pulse.services.psychometric.micro.model.SamplerItem;
import com.edge.pulse.services.psychometric.micro.model.SamplerScale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * JPA / transaction boundary for micro-engagement pull delivery (Phase 3, D1/D5).
 *
 * <p>Lists the check-ins available to a candidate (active, in-window cadences whose population
 * scope matches the user and whose test still has CONSOLIDATED scales left to consolidate), and
 * builds a sampled micro-session for a chosen cadence via the pure {@link ItemSampler}, recording
 * item exposure and reusing the existing timed-session plumbing in
 * {@link PsychometricSessionService#startSession}.
 */
@Service
@RequiredArgsConstructor
public class MicroEngagementService {

    private final AssessmentCadenceRepository cadenceRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final ScaleProgressRepository scaleProgressRepository;
    private final UserItemExposureRepository exposureRepository;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final ScoringKeyItemRepository scoringKeyItemRepository;
    private final FormAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final ResponseSessionRepository sessionRepository;
    private final PsychometricSessionService sessionService;
    private final ItemSampler sampler;

    /** Active, in-window cadences whose scope matches the user, with CONSOLIDATED scales remaining. */
    @Transactional(readOnly = true)
    public List<CheckInDto> listCheckIns(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";
        LocalDateTime now = LocalDateTime.now();

        List<CheckInDto> out = new ArrayList<>();
        for (AssessmentCadence cadence : cadenceRepository.findByActiveTrue()) {
            if (!isWindowOpen(cadence, now) || !scopeMatches(cadence, orgPath)) {
                continue;
            }
            PsychometricTest test = cadence.getTest();
            UUID testId = test.getId();

            List<PsychometricScale> consolidatedScales = scaleRepository.findByTestId(testId).stream()
                    .filter(s -> s.getResultMode() == ResultMode.CONSOLIDATED)
                    .toList();
            int scalesTotal = consolidatedScales.size();
            if (scalesTotal == 0) {
                continue; // no CONSOLIDATED scales → no consolidation work to surface
            }

            Set<UUID> consolidatedScaleIds = scaleProgressRepository.findByUserIdAndTestId(userId, testId)
                    .stream()
                    .filter(p -> p.getState() == ScaleProgressState.CONSOLIDATED)
                    .map(ScaleProgress::getScaleId)
                    .collect(java.util.stream.Collectors.toSet());
            int scalesConsolidated = 0;
            for (PsychometricScale s : consolidatedScales) {
                if (consolidatedScaleIds.contains(s.getId())) {
                    scalesConsolidated++;
                }
            }
            if (scalesConsolidated >= scalesTotal) {
                continue; // all consolidated — nothing left to check in for
            }

            out.add(new CheckInDto(
                    cadence.getId(), testId, test.getForm().getId(), test.getName(),
                    cadence.getCadence(), cadence.getMaxItemsPerAdmin(),
                    scalesConsolidated, scalesTotal));
        }
        return out;
    }

    /** Builds a micro-session for a cadence using the sampler (D5), records exposure, opens the session. */
    @Transactional
    public PsychometricSessionDto buildCheckInSession(UUID cadenceId, UUID userId) {
        AssessmentCadence cadence = cadenceRepository.findById(cadenceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PsychometricTest test = cadence.getTest();
        UUID testId = test.getId();
        UUID formId = test.getForm().getId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";
        if (!assignmentRepository.hasVisibleAssignment(formId, userId, orgPath)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        // Reject if the user already has an OPEN (completed_at IS NULL) session for this form.
        // A check-in narrows the session's itemSequence to the sampled ids; if a regular take is
        // in progress this would overwrite its item sequence and corrupt that take (C2).
        if (sessionRepository
                .findFirstByUserIdAndFormIdAndCompletedAtIsNullOrderByStartedAtDesc(userId, formId)
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finish or abandon your in-progress assessment before starting a check-in");
        }

        // ── Build the sampler input from the ACTIVE scoring key's items ────────────
        ScoringKeyVersion key = scoringKeyVersionRepository
                .findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "No active scoring key for test " + testId));
        List<ScoringKeyItem> keyItems = scoringKeyItemRepository.findByScoringKeyIdWithDetails(key.getId());

        Set<UUID> seenQuestionIds = exposureRepository.findByUserIdAndTestId(userId, testId).stream()
                .map(UserItemExposure::getQuestionId)
                .collect(java.util.stream.Collectors.toSet());

        // itemsRequired per CONSOLIDATED scale + currently-open progress (for nearest-completion).
        Map<UUID, Integer> requiredByScale = new HashMap<>();
        for (ScoringKeyItem item : keyItems) {
            requiredByScale.merge(item.getScale().getId(), 1, Integer::sum);
        }
        Map<UUID, ScaleProgress> openProgressByScale = scaleProgressRepository
                .findByUserIdAndTestId(userId, testId).stream()
                .filter(p -> p.getState() == ScaleProgressState.COLLECTING)
                .collect(java.util.stream.Collectors.toMap(
                        ScaleProgress::getScaleId, p -> p, (a, b) -> a));

        Set<UUID> consolidatedScaleIds = scaleRepository.findByTestId(testId).stream()
                .filter(s -> s.getResultMode() == ResultMode.CONSOLIDATED)
                .map(PsychometricScale::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<SamplerItem> samplerItems = new ArrayList<>();
        List<SamplerScale> samplerScales = new ArrayList<>();
        Set<UUID> scalesAdded = new HashSet<>();
        for (ScoringKeyItem item : keyItems) {
            UUID scaleId = item.getScale().getId();
            // Only sample for CONSOLIDATED scales — IMMEDIATE scales are delivered via the full take flow.
            if (!consolidatedScaleIds.contains(scaleId)) {
                continue;
            }
            UUID qId = item.getQuestion().getId();
            samplerItems.add(new SamplerItem(qId, scaleId, seenQuestionIds.contains(qId)));
            if (scalesAdded.add(scaleId)) {
                ScaleProgress p = openProgressByScale.get(scaleId);
                int required = requiredByScale.getOrDefault(scaleId, 0);
                int collected = p != null ? p.getItemsCollected() : 0;
                samplerScales.add(new SamplerScale(scaleId, required, collected));
            }
        }

        // windowId: reuse the user's open window for this test if any, else a fresh one (seed input).
        UUID windowId = openProgressByScale.values().stream()
                .map(ScaleProgress::getWindowId)
                .findFirst()
                .orElse(UUID.randomUUID());
        long seed = (long) userId.hashCode() ^ windowId.hashCode();

        List<UUID> sampledIds = sampler.next(
                new SamplerInput(samplerItems, samplerScales, cadence.getMaxItemsPerAdmin(), seed));

        // Record first-seen exposure for each sampled id not already exposed.
        LocalDateTime now = LocalDateTime.now();
        for (UUID qId : sampledIds) {
            if (!exposureRepository.existsById(new UserItemExposureId(userId, qId))) {
                exposureRepository.save(UserItemExposure.builder()
                        .userId(userId).questionId(qId).testId(testId)
                        .firstSeen(now).build());
            }
        }

        // ── Open the session via the existing timed-session plumbing, then narrow the
        //    item sequence (and question payload) to exactly the sampled ids. ──────────
        PsychometricSessionDto full = sessionService.startSession(formId, userId);

        ResponseSession session = sessionRepository.findById(full.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR));
        session.setItemSequence(new ArrayList<>(sampledIds));
        sessionRepository.save(session);

        Map<UUID, PsychometricQuestionDto> byId = new HashMap<>();
        for (PsychometricQuestionDto q : full.questions()) {
            byId.put(q.id(), q);
        }
        List<PsychometricQuestionDto> sampledQuestions = new ArrayList<>(sampledIds.size());
        for (UUID qId : sampledIds) {
            PsychometricQuestionDto q = byId.get(qId);
            if (q != null) {
                sampledQuestions.add(q);
            }
        }

        return new PsychometricSessionDto(
                full.sessionId(),
                full.testName(),
                full.testType(),
                full.instructions(),
                full.timeLimitSecs(),
                full.remainingSeconds(),
                full.serverStartEpoch(),
                new ArrayList<>(sampledIds),
                sampledQuestions);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────

    private boolean isWindowOpen(AssessmentCadence cadence, LocalDateTime now) {
        if (cadence.getStartsAt() != null && now.isBefore(cadence.getStartsAt())) {
            return false;
        }
        return cadence.getEndsAt() == null || !now.isAfter(cadence.getEndsAt());
    }

    /** NULL scope = whole org. Otherwise the user's org path must sit within the cadence's org
     *  subtree (when includeChildren) or match it exactly (D2). */
    private boolean scopeMatches(AssessmentCadence cadence, String orgPath) {
        if (cadence.getOrgUnit() == null) {
            return true;
        }
        String scopePath = cadence.getOrgUnit().getPath();
        if (scopePath == null || scopePath.isEmpty()) {
            return true;
        }
        if (cadence.isIncludeChildren()) {
            return orgPath != null && orgPath.startsWith(scopePath);
        }
        return scopePath.equals(orgPath);
    }
}
