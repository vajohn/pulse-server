package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.*;
import com.edge.pulse.data.models.*;
import com.edge.pulse.data.models.psychometric.*;
import com.edge.pulse.repositories.answer.AnswerAdjectiveRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.psychometric.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScoringServiceTest {

    @Mock PsychometricTestRepository testRepository;
    @Mock ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock NormTableVersionRepository normTableVersionRepository;
    @Mock NormEntryRepository normEntryRepository;
    @Mock NormScaleParamRepository normScaleParamRepository;
    @Mock TestResultRepository testResultRepository;
    @Mock ScaleScoreRepository scaleScoreRepository;
    @Mock PsychometricScaleRepository scaleRepository;
    @Mock AnswerScaleRepository answerScaleRepository;
    @Mock AnswerChoiceRepository answerChoiceRepository;
    @Mock AnswerAdjectiveRepository answerAdjectiveRepository;
    @Mock ScoringKeyCorrectAnswerRepository scoringKeyCorrectAnswerRepository;
    @Mock CompetencyScaleWeightRepository competencyScaleWeightRepository;
    @Mock CompetencyScoreRepository competencyScoreRepository;

    @InjectMocks ScoringService scoringService;

    private UUID surveyId, testId, sessionId, scaleId, questionId;
    private ResponseSession session;
    private PsychometricTest psychTest;
    private PsychometricScale scale;
    private ScoringKeyVersion activeKey;
    private Question question;

    @BeforeEach
    void setUp() {
        surveyId = UUID.randomUUID();
        testId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        scaleId = UUID.randomUUID();
        questionId = UUID.randomUUID();

        Form form = Form.builder().id(surveyId).title("Test Survey").build();
        session = ResponseSession.builder().id(sessionId).form(form).build();

        psychTest = PsychometricTest.builder().id(testId).form(form).name("P-Test")
                .testType(TestType.PERSONALITY).build();

        scale = PsychometricScale.builder().id(scaleId).test(psychTest).name("Resilience")
                .scoreMethod(ScoreMethod.SUM).build();

        question = Question.builder().id(questionId).build();

        activeKey = ScoringKeyVersion.builder().id(UUID.randomUUID()).test(psychTest)
                .version(1).status(ScoringKeyStatus.ACTIVE).build();
    }

    @Test
    void scoreSession_nonPsychometricSurvey_doesNothing() {
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.empty());

        scoringService.scoreSession(session);

        verifyNoInteractions(scoringKeyVersionRepository, testResultRepository, scaleScoreRepository);
    }

    @Test
    void scoreSession_alreadyScored_skips() {
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId))
                .thenReturn(Optional.of(TestResult.builder().id(UUID.randomUUID()).build()));

        scoringService.scoreSession(session);

        verify(testResultRepository, never()).save(any());
    }

    @Test
    void scoreSession_noActiveScoringKey_createsPendingResult() {
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        TestResult saved = TestResult.builder().id(UUID.randomUUID()).status(TestResultStatus.PENDING).build();
        when(testResultRepository.save(any())).thenReturn(saved);

        scoringService.scoreSession(session);

        ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(testResultRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TestResultStatus.PENDING);
        verifyNoInteractions(scaleScoreRepository);
    }

    @Test
    void scoreSession_personalityForward_usesValueDirectly() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        // FORWARD, weight=1 → raw = 3 * 1 = 3
        assertThat(ss.getRawScore()).isEqualByComparingTo(new BigDecimal("3.000"));
        assertThat(ss.getItemsAnswered()).isEqualTo(1);
        assertThat(ss.getItemsTotal()).isEqualTo(1);
    }

    @Test
    void scoreSession_personalityReverse_reversesValue() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.REVERSE, BigDecimal.ONE, null);
        // value=2 on a 1-5 scale → reversed = 5 + 1 - 2 = 4
        AnswerScale answer = buildScaleAnswer(2, 1, 5);

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getRawScore()).isEqualByComparingTo(new BigDecimal("4.000"));
    }

    @Test
    void scoreSession_cognitiveCorrectAnswer_scores1() {
        CandidateAnswer correctOpt = CandidateAnswer.builder().id(UUID.randomUUID()).build();
        ScoringKeyItem item = buildChoiceItem(BigDecimal.ONE, correctOpt);
        AnswerChoice answer = buildChoiceAnswer(correctOpt); // selected == correct

        setupFullScoreScenario(List.of(item), Map.of(), Map.of(questionId, answer));

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getRawScore()).isEqualByComparingTo(new BigDecimal("1.000"));
    }

    @Test
    void scoreSession_cognitiveWrongAnswer_scores0() {
        CandidateAnswer correctOpt = CandidateAnswer.builder().id(UUID.randomUUID()).build();
        CandidateAnswer wrongOpt = CandidateAnswer.builder().id(UUID.randomUUID()).build();
        ScoringKeyItem item = buildChoiceItem(BigDecimal.ONE, correctOpt);
        AnswerChoice answer = buildChoiceAnswer(wrongOpt); // selected != correct

        setupFullScoreScenario(List.of(item), Map.of(), Map.of(questionId, answer));

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getRawScore()).isEqualByComparingTo(BigDecimal.ZERO.setScale(3));
    }

    @Test
    void scoreSession_notAnsweredItem_countedInTotalButNotAnswered() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);

        setupFullScoreScenario(List.of(item), Map.of(), Map.of()); // no answers provided

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getItemsAnswered()).isEqualTo(0);
        assertThat(ss.getItemsTotal()).isEqualTo(1);
        assertThat(ss.getRawScore()).isEqualByComparingTo(BigDecimal.ZERO.setScale(3));
    }

    @Test
    void scoreSession_noNormTable_savesNullStenAndPercentile() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.empty());

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getStenScore()).isNull();
        assertThat(ss.getPercentile()).isNull();
    }

    @Test
    void scoreSession_withParametricNorm_appliesStandardizedScore() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);
        // raw = 3 (value 3, FORWARD, weight 1). mean=3, sd=1 → z=0 → sten=5.5 (decimal), percentile=50.00
        NormTableVersion norm = NormTableVersion.builder().id(UUID.randomUUID()).build();
        NormScaleParam param = NormScaleParam.builder()
                .normTable(norm).scale(scale)
                .mean(new BigDecimal("3")).sd(new BigDecimal("1"))
                .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build();

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.of(norm));
        when(normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), scaleId))
                .thenReturn(Optional.of(param));

        scoringService.scoreSession(session);

        ArgumentCaptor<ScaleScore> captor = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(captor.capture());
        ScaleScore ss = captor.getAllValues().stream()
                .filter(s -> s.getScale().getId().equals(scaleId)).findFirst().orElseThrow();
        assertThat(ss.getStenScore()).isEqualByComparingTo(new BigDecimal("5.5"));
        assertThat(ss.getPercentile()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(ss.getZScore()).isEqualByComparingTo("0.000");
    }

    @Test
    void scoreSession_persistsTestResultWithStatusScored() {
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());

        scoringService.scoreSession(session);

        ArgumentCaptor<TestResult> captor = ArgumentCaptor.forClass(TestResult.class);
        verify(testResultRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TestResultStatus.SCORED);
        assertThat(captor.getValue().getScoredAt()).isNotNull();
        assertThat(captor.getValue().getScoringKeyVersion()).isEqualTo(activeKey);
    }

    // ── Competency scoring ────────────────────────────────────────────────────────

    @Test
    void scoreCompetencies_computesWeightedNormalizedScore_forwardDirection() {
        // Arrange: one personality item → sten=6.5 via parametric norm (decimal STEN).
        // raw=3 (value 3, FORWARD, weight 1); mean=2, sd=2 → z=0.5 → sten=5.5+2*0.5=6.5
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);

        NormTableVersion norm = NormTableVersion.builder().id(UUID.randomUUID()).build();
        NormScaleParam param = NormScaleParam.builder()
                .normTable(norm).scale(scale)
                .mean(new BigDecimal("2")).sd(new BigDecimal("2"))
                .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build();

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.of(norm));
        when(normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), scaleId))
                .thenReturn(Optional.of(param));

        Competency competency = Competency.builder().id(UUID.randomUUID()).name("Leadership").build();
        CompetencyScaleWeight weight = CompetencyScaleWeight.builder()
                .competency(competency).scale(scale)
                .weight(BigDecimal.ONE).direction(ScoreDirection.FORWARD).build();

        when(competencyScaleWeightRepository.findByScaleIdIn(any()))
                .thenReturn(List.of(weight));
        when(competencyScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scoringService.scoreSession(session);

        ArgumentCaptor<CompetencyScore> captor = ArgumentCaptor.forClass(CompetencyScore.class);
        verify(competencyScoreRepository).save(captor.capture());
        // sten=6.5, FORWARD → normalized = 6.5/1.0 = 6.5 (weight=1, weightedSum=6.5)
        assertThat(captor.getValue().getScore()).isEqualByComparingTo(new BigDecimal("6.500"));
        assertThat(captor.getValue().getCompetency()).isEqualTo(competency);
    }

    @Test
    void scoreCompetencies_reverseDirection_invertsStens() {
        // Arrange: sten=3.0, REVERSE → 11-3=8.
        // raw=1 (value 1, FORWARD, weight 1); mean=6, sd=4 → z=-1.25 → sten=5.5+2*(-1.25)=3.0
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(1, 1, 5);

        NormTableVersion norm = NormTableVersion.builder().id(UUID.randomUUID()).build();
        NormScaleParam param = NormScaleParam.builder()
                .normTable(norm).scale(scale)
                .mean(new BigDecimal("6")).sd(new BigDecimal("4"))
                .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build();

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.of(norm));
        when(normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), scaleId))
                .thenReturn(Optional.of(param));

        Competency competency = Competency.builder().id(UUID.randomUUID()).name("Integrity").build();
        CompetencyScaleWeight weight = CompetencyScaleWeight.builder()
                .competency(competency).scale(scale)
                .weight(BigDecimal.ONE).direction(ScoreDirection.REVERSE).build();

        when(competencyScaleWeightRepository.findByScaleIdIn(any()))
                .thenReturn(List.of(weight));
        when(competencyScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scoringService.scoreSession(session);

        ArgumentCaptor<CompetencyScore> captor = ArgumentCaptor.forClass(CompetencyScore.class);
        verify(competencyScoreRepository).save(captor.capture());
        // sten=3, REVERSE → 11-3=8; normalized=8.000
        assertThat(captor.getValue().getScore()).isEqualByComparingTo(new BigDecimal("8.000"));
    }

    @Test
    void scoreCompetencies_skipsWhenNoStenScores() {
        // When no norm table → no sten scores → competency scoring skipped entirely
        ScoringKeyItem item = buildScaleItem(ScoreDirection.FORWARD, BigDecimal.ONE, null);
        AnswerScale answer = buildScaleAnswer(3, 1, 5);

        setupFullScoreScenario(List.of(item), Map.of(questionId, answer), Map.of());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.empty());  // no norm → sten will be null

        scoringService.scoreSession(session);

        // competencyScaleWeightRepository should NOT be queried — no sten scores exist
        verify(competencyScaleWeightRepository, never()).findByScaleIdIn(any());
        verify(competencyScoreRepository, never()).save(any());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private void setupFullScoreScenario(List<ScoringKeyItem> items,
                                         Map<UUID, AnswerScale> scaleAnswers,
                                         Map<UUID, AnswerChoice> choiceAnswers) {
        when(testRepository.findByFormId(surveyId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeKey));
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(activeKey.getId()))
                .thenReturn(items);

        // Build answer lists from maps
        List<AnswerScale> scaleList = new ArrayList<>(scaleAnswers.values());
        List<AnswerChoice> choiceList = new ArrayList<>(choiceAnswers.values());

        when(answerScaleRepository.findCurrentBySessionId(sessionId)).thenReturn(scaleList);
        when(answerChoiceRepository.findCurrentBySessionId(sessionId)).thenReturn(choiceList);
        // Adjective and multi-choice correct-answer repos always return empty for existing tests
        lenient().when(answerAdjectiveRepository.findCurrentBySessionId(sessionId))
                .thenReturn(List.of());
        lenient().when(scoringKeyCorrectAnswerRepository.findByItemIdIn(any()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(scale));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.empty());

        TestResult savedResult = TestResult.builder().id(UUID.randomUUID())
                .status(TestResultStatus.SCORED).scoringKeyVersion(activeKey).build();
        when(testResultRepository.save(any())).thenReturn(savedResult);
        // Default: no competency weights — lenient because tests without a norm table
        // have null sten scores → scoreCompetencies() exits early → this stub is unused
        lenient().when(competencyScaleWeightRepository.findByScaleIdIn(any())).thenReturn(List.of());
        when(scaleScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ScoringKeyItem buildScaleItem(ScoreDirection direction, BigDecimal weight,
                                           CandidateAnswer correctAnswer) {
        Question q = Question.builder().id(questionId).questionType(QuestionType.SCALE).build();
        return ScoringKeyItem.builder()
                .id(UUID.randomUUID())
                .scale(scale)
                .question(q)
                .direction(direction)
                .weight(weight)
                .correctAnswer(correctAnswer)
                .build();
    }

    private ScoringKeyItem buildChoiceItem(BigDecimal weight, CandidateAnswer correctAnswer) {
        Question q = Question.builder().id(questionId).questionType(QuestionType.CHOICE_SINGLE).build();
        return ScoringKeyItem.builder()
                .id(UUID.randomUUID())
                .scale(scale)
                .question(q)
                .direction(ScoreDirection.FORWARD)
                .weight(weight)
                .correctAnswer(correctAnswer)
                .build();
    }

    private AnswerScale buildScaleAnswer(int value, int min, int max) {
        AnswerSubmission submission = AnswerSubmission.builder()
                .id(UUID.randomUUID()).question(question).build();
        return AnswerScale.builder()
                .id(UUID.randomUUID())
                .submission(submission)
                .value(value).minValue(min).maxValue(max)
                .build();
    }

    private AnswerChoice buildChoiceAnswer(CandidateAnswer selected) {
        AnswerSubmission submission = AnswerSubmission.builder()
                .id(UUID.randomUUID()).question(question).build();
        return AnswerChoice.builder()
                .id(UUID.randomUUID())
                .submission(submission)
                .candidateAnswer(selected)
                .build();
    }
}
