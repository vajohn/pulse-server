package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.BatchSubmitRequest;
import com.edge.pulse.data.dto.OpenSessionRequest;
import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.dto.psychometric.HeartbeatResponse;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.TestResult;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.TestResultRepository;
import com.edge.pulse.services.AnswerService;
import com.edge.pulse.services.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PsychometricSessionServiceTest {

    @Mock SessionService sessionService;
    @Mock AnswerService answerService;
    @Mock ResponseSessionRepository sessionRepository;
    @Mock PsychometricTestRepository testRepository;
    @Mock QuestionRepository questionRepository;
    @Mock FormAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock TestResultRepository resultRepository;
    @Mock com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock Clock clock;

    @InjectMocks PsychometricSessionService service;

    private UUID userId, surveyId, sessionId;
    private User candidateUser;
    private Form survey;
    private PsychometricTest psychTest;
    private ResponseSession session;
    private static final String ORG_PATH = "/hq/ops";
    private static final long FIXED_EPOCH = 1_700_000_000_000L;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        surveyId  = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        OrganizationalUnit orgUnit = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path(ORG_PATH).build();
        candidateUser = User.builder().id(userId).orgUnit(orgUnit).build();

        survey = Form.builder().id(surveyId).title("Resilience Profile").build();

        psychTest = PsychometricTest.builder()
                .id(UUID.randomUUID())
                .form(survey)
                .name("Resilience Profile")
                .instructions("Answer honestly.")
                .testType(TestType.PERSONALITY)
                .timeLimitSecs(null)
                .build();

        session = ResponseSession.builder()
                .id(sessionId)
                .form(survey)
                .user(candidateUser)
                .isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .build();
    }

    // ── toQuestionDto / bilingual delivery ───────────────────────────────────

    @Test
    void startSession_questionDto_includesBodyAr_whenSet() {
        // Arrange: question with an Arabic body variant (e.g. Q3_ARA equivalent)
        String enBody = "I stay calm under pressure.";
        String arBody = "أتحلى بالهدوء تحت الضغط.";
        Question q = Question.builder()
                .id(UUID.randomUUID())
                .body(enBody)
                .bodyAr(arBody)
                .questionType(QuestionType.SCALE)
                .displayOrder(1)
                .scaleMin(1).scaleMax(5)
                .minLabel("SD").maxLabel("SA")
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(true);
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(questionRepository.findActiveByFormIdWithAnswers(surveyId)).thenReturn(List.of(q));
        when(sessionService.openOrResumeSession(eq(surveyId), eq(userId), any(OpenSessionRequest.class)))
                .thenReturn(session);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PsychometricSessionDto dto = service.startSession(surveyId, userId);

        // Assert: Arabic body must be present in the question DTO
        assertThat(dto.questions()).hasSize(1);
        var questionDto = dto.questions().get(0);
        assertThat(questionDto.body()).isEqualTo(enBody);
        assertThat(questionDto.bodyAr()).isEqualTo(arBody);
    }

    @Test
    void startSession_questionDto_bodyAr_isNull_whenNotAuthored() {
        // Arrange: question with no Arabic variant (bodyAr not set)
        Question q = Question.builder()
                .id(UUID.randomUUID())
                .body("How do you handle conflict?")
                .bodyAr(null)
                .questionType(QuestionType.SCALE)
                .displayOrder(1)
                .scaleMin(1).scaleMax(5)
                .minLabel("Never").maxLabel("Always")
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(true);
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(questionRepository.findActiveByFormIdWithAnswers(surveyId)).thenReturn(List.of(q));
        when(sessionService.openOrResumeSession(eq(surveyId), eq(userId), any(OpenSessionRequest.class)))
                .thenReturn(session);
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Act
        PsychometricSessionDto dto = service.startSession(surveyId, userId);

        // Assert: bodyAr is null — Flutter localizedBody() falls back to body (EN)
        assertThat(dto.questions()).hasSize(1);
        assertThat(dto.questions().get(0).bodyAr()).isNull();
    }

    // ── startSession ─────────────────────────────────────────────────────────

    @Test
    void startSession_newSession_populatesPsychometricFields() {
        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(true);
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));

        Question q1 = Question.builder().id(UUID.randomUUID())
                .body("Q1").questionType(QuestionType.SCALE).displayOrder(1)
                .scaleMin(1).scaleMax(5).minLabel("SD").maxLabel("SA").build();
        Question q2 = Question.builder().id(UUID.randomUUID())
                .body("Q2").questionType(QuestionType.CHOICE).displayOrder(2).build();

        when(questionRepository.findActiveByFormIdWithAnswers(surveyId)).thenReturn(List.of(q1, q2));
        when(sessionService.openOrResumeSession(eq(surveyId), eq(userId), any(OpenSessionRequest.class)))
                .thenReturn(session); // session.itemSequence == null → new session
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PsychometricSessionDto dto = service.startSession(surveyId, userId);

        assertThat(dto.testName()).isEqualTo("Resilience Profile");
        assertThat(dto.testType()).isEqualTo("PERSONALITY");
        assertThat(dto.timeLimitSecs()).isNull();
        assertThat(dto.remainingSeconds()).isNull();
        assertThat(dto.itemSequence()).hasSize(2);
        assertThat(dto.questions()).hasSize(2);
        // Verify session was saved with psychometric fields
        verify(sessionRepository).save(any(ResponseSession.class));
    }

    @Test
    void startSession_resumedSession_preservesExistingFields() {
        long existingEpoch = FIXED_EPOCH - 60_000L;
        List<UUID> existingSequence = List.of(UUID.randomUUID(), UUID.randomUUID());
        ResponseSession resumed = ResponseSession.builder()
                .id(sessionId).form(survey).user(candidateUser).isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .itemSequence(existingSequence)
                .serverStartEpoch(existingEpoch)
                .timeLimitSecs(1800)
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(true);
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(questionRepository.findActiveByFormIdWithAnswers(surveyId)).thenReturn(List.of());
        when(sessionService.openOrResumeSession(eq(surveyId), eq(userId), any(OpenSessionRequest.class)))
                .thenReturn(resumed);

        PsychometricSessionDto dto = service.startSession(surveyId, userId);

        // Timer should have ticked down: elapsed = 60s, allowed = 1800s → remaining = 1740s
        assertThat(dto.remainingSeconds()).isEqualTo(1740L);
        assertThat(dto.itemSequence()).isEqualTo(existingSequence);
        // sessionRepository.save should NOT be called (no new session)
    }

    @Test
    void startSession_throws403_whenNoAssignment() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(false);

        assertThatThrownBy(() -> service.startSession(surveyId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── getHeartbeat ─────────────────────────────────────────────────────────

    @Test
    void getHeartbeat_timedSession_returnsRemaining() {
        long epochNow = FIXED_EPOCH;
        long epochStart = epochNow - 120_000L; // 2 minutes elapsed
        ResponseSession timedSession = ResponseSession.builder()
                .id(sessionId).form(survey).user(candidateUser).isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .serverStartEpoch(epochStart)
                .timeLimitSecs(600) // 10 minutes
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(epochNow));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(timedSession));

        HeartbeatResponse response = service.getHeartbeat(sessionId, userId);

        // 600 - 120 = 480 seconds remaining
        assertThat(response.remainingSeconds()).isEqualTo(480L);
    }

    @Test
    void getHeartbeat_untimedSession_returnsNull() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        HeartbeatResponse response = service.getHeartbeat(sessionId, userId);

        assertThat(response.remainingSeconds()).isNull();
    }

    // ── logFocusEvent ─────────────────────────────────────────────────────────

    @Test
    void logFocusEvent_incrementsCount() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.logFocusEvent(sessionId, userId);

        verify(sessionRepository).save(any(ResponseSession.class));
        assertThat(session.getFocusLossCount()).isEqualTo(1);
    }

    @Test
    void logFocusEvent_silentlyIgnoresCompletedSession() {
        session.setCompletedAt(LocalDateTime.now());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        service.logFocusEvent(sessionId, userId);

        assertThat(session.getFocusLossCount()).isEqualTo(0);
    }

    // ── completeSession ───────────────────────────────────────────────────────

    @Test
    void completeSession_submitsAnswersAndReturnsResult() {
        UUID resultId = UUID.randomUUID();
        TestResult result = TestResult.builder()
                .id(resultId).test(psychTest).session(session)
                .status(TestResultStatus.SCORED)
                .focusLossCount(0)
                .scoredAt(LocalDateTime.now())
                .build();

        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(sessionService.completeSession(sessionId, userId)).thenReturn(session);
        when(resultRepository.findBySessionId(sessionId)).thenReturn(Optional.of(result));

        SubmitAnswerRequest answer = new SubmitAnswerRequest(
                UUID.randomUUID(), QuestionType.SCALE, null, 3, 1, 5, null, null, null, null);
        BatchSubmitRequest request = new BatchSubmitRequest(List.of(answer));

        CandidateTestResultDto dto = service.completeSession(sessionId, userId, request);

        assertThat(dto.resultId()).isEqualTo(resultId);
        assertThat(dto.status()).isEqualTo(TestResultStatus.SCORED);
        verify(answerService).submitAll(eq(sessionId), any());
        verify(sessionService).completeSession(sessionId, userId);
    }

    @Test
    void completeSession_throws404_whenSessionNotFound() {
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        BatchSubmitRequest request = new BatchSubmitRequest(List.of());
        assertThatThrownBy(() -> service.completeSession(sessionId, userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void completeSession_throws403_whenTimeLimitExceeded() {
        // Session started 40 minutes ago; time limit is 30 minutes → 10 min over deadline
        long limitSecs = 1800; // 30 min
        long startEpoch = FIXED_EPOCH - (40 * 60 * 1000L); // 40 min ago
        ResponseSession timedSession = ResponseSession.builder()
                .id(sessionId).form(survey).user(candidateUser).isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .serverStartEpoch(startEpoch)
                .timeLimitSecs((int) limitSecs)
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(timedSession));

        BatchSubmitRequest request = new BatchSubmitRequest(List.of());
        assertThatThrownBy(() -> service.completeSession(sessionId, userId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void completeSession_acceptsSubmission_withinGraceWindow() {
        // Session is 1 minute over the limit (within 30 s grace → should pass)
        // Actually 30 s exactly over limit is the boundary; 60 s over should be rejected.
        // This test verifies the *accept* path: elapsed = timeLimitSecs + 20 s
        long limitSecs = 600; // 10 min
        long elapsedMs = (limitSecs + 20L) * 1000L; // 20 s into grace window
        long startEpoch = FIXED_EPOCH - elapsedMs;
        ResponseSession timedSession = ResponseSession.builder()
                .id(sessionId).form(survey).user(candidateUser).isAnonymous(false)
                .startedAt(LocalDateTime.now())
                .serverStartEpoch(startEpoch)
                .timeLimitSecs((int) limitSecs)
                .build();

        UUID resultId = UUID.randomUUID();
        TestResult result = TestResult.builder()
                .id(resultId).test(psychTest).session(timedSession)
                .status(TestResultStatus.SCORED)
                .focusLossCount(0)
                .scoredAt(LocalDateTime.now())
                .build();

        when(clock.instant()).thenReturn(Instant.ofEpochMilli(FIXED_EPOCH));
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(timedSession));
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(sessionService.completeSession(sessionId, userId)).thenReturn(timedSession);
        when(resultRepository.findBySessionId(sessionId)).thenReturn(Optional.of(result));

        BatchSubmitRequest request = new BatchSubmitRequest(List.of());
        CandidateTestResultDto dto = service.completeSession(sessionId, userId, request);

        assertThat(dto.resultId()).isEqualTo(resultId);
    }
}
