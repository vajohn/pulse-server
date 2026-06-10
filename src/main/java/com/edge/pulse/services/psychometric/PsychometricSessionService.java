package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.BatchSubmitRequest;
import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.OpenSessionRequest;
import com.edge.pulse.data.dto.psychometric.HeartbeatResponse;
import com.edge.pulse.data.dto.psychometric.PsychometricQuestionDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.TestResult;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestResultRepository;
import com.edge.pulse.services.AnswerService;
import com.edge.pulse.services.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Candidate-facing session management for psychometric tests.
 *
 * <p>Handles the four candidate session endpoints:
 * <ul>
 *   <li>{@code POST /api/psychometric/sessions} — start or resume</li>
 *   <li>{@code GET  /api/psychometric/sessions/{id}/time} — heartbeat</li>
 *   <li>{@code POST /api/psychometric/sessions/{id}/focus-event} — log focus loss</li>
 *   <li>{@code POST /api/psychometric/sessions/{id}/complete} — batch submit + score</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PsychometricSessionService {

    private final SessionService sessionService;
    private final AnswerService answerService;
    private final ResponseSessionRepository sessionRepository;
    private final PsychometricTestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final FormAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TestResultRepository resultRepository;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final ScoringKeyItemRepository scoringKeyItemRepository;
    private final Clock clock;

    /**
     * Opens or resumes a psychometric session and returns the full question payload.
     *
     * <p>For a new session: sets {@code serverStartEpoch}, {@code timeLimitSecs},
     * and shuffles questions into {@code itemSequence}.
     * For a resumed session: preserves the original values so the timer is not reset.
     */
    @Transactional
    public PsychometricSessionDto startSession(UUID formId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";
        if (!assignmentRepository.hasVisibleAssignment(formId, userId, orgPath)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        PsychometricTest test = testRepository.findByFormId(formId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No psychometric test found for form " + formId));

        // findActiveByFormIdWithAnswers JOIN FETCHes candidateAnswers in one query
        // to avoid N+1 when building PsychometricQuestionDto for each question.
        List<Question> questions = questionRepository.findActiveByFormIdWithAnswers(formId);

        ResponseSession session = sessionService.openOrResumeSession(
                formId, userId, new OpenSessionRequest(false));

        // Populate psychometric fields on first creation only
        if (session.getItemSequence() == null) {
            List<UUID> shuffled = new ArrayList<>(
                    questions.stream().map(Question::getId).toList());
            Collections.shuffle(shuffled);
            session.setItemSequence(shuffled);
            session.setServerStartEpoch(Instant.now(clock).toEpochMilli());
            session.setTimeLimitSecs(test.getTimeLimitSecs());
            session = sessionRepository.save(session);
        }

        Long remainingSeconds = computeRemainingSeconds(session);

        // Build partialCredit lookup from the active scoring key (if one exists).
        // This is a best-effort enrichment: if no ACTIVE key exists yet the flag
        // defaults to false for all items, which is functionally correct.
        Map<UUID, Boolean> partialCreditByQuestion = buildPartialCreditMap(test.getId());

        List<PsychometricQuestionDto> questionDtos = buildOrderedQuestionDtos(
                session.getItemSequence(), questions, partialCreditByQuestion);

        return new PsychometricSessionDto(
                session.getId(),
                test.getName(),
                test.getTestType().name(),
                test.getInstructions(),
                test.getTimeLimitSecs(),
                remainingSeconds,
                session.getServerStartEpoch(),
                session.getItemSequence(),
                questionDtos);
    }

    /**
     * Returns the remaining time for a timed session, or {@code null} for untimed sessions.
     */
    @Transactional(readOnly = true)
    public HeartbeatResponse getHeartbeat(UUID sessionId, UUID userId) {
        ResponseSession session = getAndVerifySession(sessionId, userId);
        return new HeartbeatResponse(computeRemainingSeconds(session));
    }

    /**
     * Increments the focus-loss counter by 1.
     *
     * <p>Focus loss is defined as the app entering background state
     * ({@code AppLifecycleState.paused} on Flutter). The counter is copied
     * to {@link com.edge.pulse.data.models.psychometric.TestResult#getFocusLossCount()}
     * at scoring time.
     */
    @Transactional
    public void logFocusEvent(UUID sessionId, UUID userId) {
        ResponseSession session = getAndVerifySession(sessionId, userId);
        if (session.getCompletedAt() != null) {
            // Silently ignore focus events on already-completed sessions
            return;
        }
        session.setFocusLossCount(session.getFocusLossCount() + 1);
        sessionRepository.save(session);
    }

    /**
     * Batch-submits all answers, marks the session complete, triggers scoring,
     * and returns the resulting {@link CandidateTestResultDto}.
     *
     * <p>Scoring is synchronous — a {@link TestResult} is always persisted
     * (either {@code SCORED} or {@code PENDING} when no active scoring key exists).
     */
    @Transactional
    public CandidateTestResultDto completeSession(UUID sessionId, UUID userId,
                                                   BatchSubmitRequest request) {
        ResponseSession session = getAndVerifySession(sessionId, userId);

        // Enforce time limit: reject submissions more than 30 s past the deadline.
        // The 30 s grace window absorbs legitimate network latency without giving
        // candidates a meaningful advantage on short cognitive tests.
        enforceTimeLimit(session);

        // Verify this form has a psychometric test (guard against misrouted calls)
        testRepository.findByFormId(session.getForm().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Session is not for a psychometric test"));

        answerService.submitAll(sessionId, request.answers());
        sessionService.completeSession(sessionId, userId);

        TestResult result = resultRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Scoring did not produce a result for session " + sessionId));

        return toResultDto(result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ResponseSession getAndVerifySession(UUID sessionId, UUID userId) {
        ResponseSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Session not found: " + sessionId));
        if (session.isAnonymous() || session.getUser() == null
                || !session.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Session does not belong to authenticated user");
        }
        return session;
    }

    /**
     * Enforces the time limit for timed psychometric tests.
     *
     * <p>Rejects the submission with 403 FORBIDDEN if the elapsed time exceeds
     * the test's time limit by more than the 30-second grace window. The grace
     * window absorbs legitimate network latency without giving candidates a
     * meaningful advantage on cognitive tests.
     *
     * <p>No-op for untimed sessions (timeLimitSecs == null).
     */
    private void enforceTimeLimit(ResponseSession session) {
        if (session.getTimeLimitSecs() == null || session.getServerStartEpoch() == null) {
            return; // untimed session — no enforcement
        }
        long elapsedSecs = (Instant.now(clock).toEpochMilli() - session.getServerStartEpoch()) / 1000L;
        long graceSecs = (long) session.getTimeLimitSecs() + 30L;
        if (elapsedSecs > graceSecs) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Test time limit exceeded — submission rejected");
        }
    }

    /** Computes remaining seconds; returns {@code null} for untimed sessions. */
    private Long computeRemainingSeconds(ResponseSession session) {
        if (session.getTimeLimitSecs() == null || session.getServerStartEpoch() == null) {
            return null;
        }
        long elapsedMs = Instant.now(clock).toEpochMilli() - session.getServerStartEpoch();
        long allowedMs = (long) session.getTimeLimitSecs() * 1000L;
        return Math.max(0L, (allowedMs - elapsedMs) / 1000L);
    }

    /**
     * Builds the ordered question DTO list by mapping each UUID in {@code itemSequence}
     * to the corresponding {@link Question}, preserving the randomised order.
     */
    private List<PsychometricQuestionDto> buildOrderedQuestionDtos(
            List<UUID> itemSequence, List<Question> questions,
            Map<UUID, Boolean> partialCreditByQuestion) {
        Map<UUID, Question> byId = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));
        return itemSequence.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(q -> toQuestionDto(q, partialCreditByQuestion.getOrDefault(q.getId(), false)))
                .toList();
    }

    /**
     * Batch-loads the active scoring key items for the test and returns a map of
     * questionId → partialCredit flag. Returns an empty map if no ACTIVE key exists.
     */
    private Map<UUID, Boolean> buildPartialCreditMap(UUID testId) {
        return scoringKeyVersionRepository
                .findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE)
                .map(key -> {
                    List<ScoringKeyItem> items =
                            scoringKeyItemRepository.findByScoringKeyIdWithDetails(key.getId());
                    return items.stream()
                            .collect(Collectors.toMap(
                                    item -> item.getQuestion().getId(),
                                    ScoringKeyItem::isPartialCredit,
                                    (a, b) -> a || b)); // if same question on multiple items, OR the flags
                })
                .orElse(Map.of());
    }

    private PsychometricQuestionDto toQuestionDto(Question q, boolean partialCredit) {
        List<CandidateAnswerDto> choices = q.getCandidateAnswers() == null
                ? List.of()
                : q.getCandidateAnswers().stream()
                        .map(ca -> new CandidateAnswerDto(ca.getId(), ca.getLabel(), ca.getLabelAr(), ca.getDisplayOrder()))
                        .toList();

        com.edge.pulse.data.enums.QuestionType type = q.getQuestionType();
        boolean allowMultipleSelect = type == com.edge.pulse.data.enums.QuestionType.CHOICE_MULTIPLE;

        // ADJECTIVE_CHECKLIST: adjectives come from question.subjectLabels (JSONB)
        List<String> adjectives = (type == com.edge.pulse.data.enums.QuestionType.ADJECTIVE_CHECKLIST)
                ? q.getSubjectLabels()
                : null;

        return new PsychometricQuestionDto(
                q.getId(),
                q.getBody(),
                type.name(),
                q.getDisplayOrder(),
                q.getScaleMin(),
                q.getScaleMax(),
                q.getMinLabel(),
                q.getMaxLabel(),
                choices,
                allowMultipleSelect,
                partialCredit,
                adjectives,
                q.getForcedChoicePairs());
    }

    private CandidateTestResultDto toResultDto(TestResult r) {
        return new CandidateTestResultDto(
                r.getId(),
                r.getTest().getId(),
                r.getTest().getName(),
                r.getTest().getTestType().name(),
                r.getStatus(),
                r.getSession().getCompletedAt(),
                r.getScoredAt(),
                r.getFocusLossCount());
    }
}
