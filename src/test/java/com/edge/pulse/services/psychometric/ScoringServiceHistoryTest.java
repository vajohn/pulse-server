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
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4 (§12): verifies {@code recordCapabilityHistory} appends an immutable
 * {@link CapabilityScoreHistory} row and upserts {@link CapabilityProfileCurrent} for every
 * eligible (FINAL, VALID, non-restricted, leaf, normed, real-user) scale score, computing the
 * trend (delta + n_administrations + norm_changed) against any prior current row — and skips
 * restricted / INVALID / composite / PROVISIONAL cases (D3/D4/D5).
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceHistoryTest {

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
    @Mock UserItemExposureRepository userItemExposureRepository;
    @Mock ScaleProgressRepository scaleProgressRepository;
    @Mock CapabilityScoreHistoryRepository capabilityScoreHistoryRepository;
    @Mock CapabilityProfileCurrentRepository capabilityProfileCurrentRepository;

    @InjectMocks ScoringService scoringService;

    private UUID formId, testId, sessionId, scaleId, questionId, userId;
    private ResponseSession session;
    private PsychometricTest psychTest;
    private PsychometricScale scale;
    private ScoringKeyVersion activeKey;
    private NormTableVersion normV1;

    @BeforeEach
    void setUp() {
        formId = UUID.randomUUID();
        testId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        scaleId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        userId = UUID.randomUUID();

        Form form = Form.builder().id(formId).title("P").build();
        User user = User.builder().id(userId).build();
        session = ResponseSession.builder().id(sessionId).form(form).user(user).build();
        psychTest = PsychometricTest.builder().id(testId).form(form).name("P-Test")
                .testType(TestType.PERSONALITY).build();
        scale = PsychometricScale.builder().id(scaleId).test(psychTest).name("Resilience")
                .scoreMethod(ScoreMethod.SUM).build();
        activeKey = ScoringKeyVersion.builder().id(UUID.randomUUID()).test(psychTest)
                .version(1).status(ScoringKeyStatus.ACTIVE).build();
        normV1 = NormTableVersion.builder().id(UUID.randomUUID())
                .normStrategy(NormStrategyType.PARAMETRIC).build();
    }

    /** Drives a single normed leaf-scale scoring run against {@code targetScale}. */
    private void setupRun(PsychometricScale targetScale, NormTableVersion norm,
                          BigDecimal mean, BigDecimal sd) {
        Question q = Question.builder().id(questionId).questionType(QuestionType.SCALE).build();
        ScoringKeyItem item = ScoringKeyItem.builder()
                .id(UUID.randomUUID()).scale(targetScale).question(q)
                .direction(ScoreDirection.FORWARD).weight(BigDecimal.ONE).build();

        AnswerSubmission submission = AnswerSubmission.builder()
                .id(UUID.randomUUID()).question(q).build();
        AnswerScale answer = AnswerScale.builder()
                .id(UUID.randomUUID()).submission(submission)
                .value(3).minValue(1).maxValue(5).build();

        when(testRepository.findByFormId(formId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeKey));
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(activeKey.getId()))
                .thenReturn(List.of(item));
        when(answerScaleRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of(answer));
        when(answerChoiceRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(answerAdjectiveRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(scoringKeyCorrectAnswerRepository.findByItemIdIn(any())).thenReturn(List.of());
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(targetScale));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.ofNullable(norm));
        if (norm != null) {
            NormScaleParam param = NormScaleParam.builder()
                    .normTable(norm).scale(targetScale).mean(mean).sd(sd)
                    .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                    .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build();
            when(normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), targetScale.getId()))
                    .thenReturn(Optional.of(param));
        }
        // Return the real argument so the hook sees the populated TestResult (session/test/scoredAt/norm).
        when(testResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(competencyScaleWeightRepository.findByScaleIdIn(any())).thenReturn(List.of());
        when(scaleScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(capabilityScoreHistoryRepository.existsByScaleIdAndResultId(any(), any()))
                .thenReturn(false);
    }

    @Test
    void finalValidLeafScale_appendsHistory_andUpsertsCurrent_firstAdministration() {
        // mean=3, sd=1 → raw=3 → z=0 → sten=5.5
        setupRun(scale, normV1, new BigDecimal("3"), new BigDecimal("1"));
        when(capabilityProfileCurrentRepository.findByUserIdAndScaleId(userId, scaleId))
                .thenReturn(Optional.empty());

        scoringService.scoreSession(session);

        ArgumentCaptor<CapabilityScoreHistory> hCap =
                ArgumentCaptor.forClass(CapabilityScoreHistory.class);
        verify(capabilityScoreHistoryRepository).save(hCap.capture());
        CapabilityScoreHistory h = hCap.getValue();
        assertThat(h.getUserId()).isEqualTo(userId);
        assertThat(h.getScaleId()).isEqualTo(scaleId);
        assertThat(h.getStenScore()).isEqualByComparingTo(new BigDecimal("5.5"));
        assertThat(h.getNormTableVersionId()).isEqualTo(normV1.getId());

        ArgumentCaptor<CapabilityProfileCurrent> cCap =
                ArgumentCaptor.forClass(CapabilityProfileCurrent.class);
        verify(capabilityProfileCurrentRepository).save(cCap.capture());
        CapabilityProfileCurrent c = cCap.getValue();
        assertThat(c.getPrevStenScore()).isNull();
        assertThat(c.getStenDelta()).isNull();
        assertThat(c.isNormChanged()).isFalse();
        assertThat(c.getNAdministrations()).isEqualTo(1);
        assertThat(c.getStenScore()).isEqualByComparingTo(new BigDecimal("5.5"));
    }

    @Test
    void secondAdministration_setsTrendDelta_and_nAdministrations2() {
        // mean=2, sd=2 → raw=3 → z=0.5 → sten=6.5
        setupRun(scale, normV1, new BigDecimal("2"), new BigDecimal("2"));
        CapabilityProfileCurrent prior = CapabilityProfileCurrent.builder()
                .userId(userId).scaleId(scaleId).testId(testId)
                .latestResultId(UUID.randomUUID())
                .stenScore(new BigDecimal("5.0")).normTableVersionId(normV1.getId())
                .scoredAt(LocalDateTime.now().minusDays(30)).nAdministrations(1).build();
        when(capabilityProfileCurrentRepository.findByUserIdAndScaleId(userId, scaleId))
                .thenReturn(Optional.of(prior));

        scoringService.scoreSession(session);

        ArgumentCaptor<CapabilityProfileCurrent> cCap =
                ArgumentCaptor.forClass(CapabilityProfileCurrent.class);
        verify(capabilityProfileCurrentRepository).save(cCap.capture());
        CapabilityProfileCurrent c = cCap.getValue();
        assertThat(c.getPrevStenScore()).isEqualByComparingTo(new BigDecimal("5.0"));
        assertThat(c.getStenScore()).isEqualByComparingTo(new BigDecimal("6.5"));
        assertThat(c.getStenDelta()).isEqualByComparingTo(new BigDecimal("1.5"));
        assertThat(c.isNormChanged()).isFalse();
        assertThat(c.getNAdministrations()).isEqualTo(2);
    }

    @Test
    void normVersionChange_setsNormChangedFlag_butDoesNotRescore() {
        NormTableVersion normV2 = NormTableVersion.builder().id(UUID.randomUUID())
                .normStrategy(NormStrategyType.PARAMETRIC).build();
        // mean=3, sd=1 → sten=5.5 under v2
        setupRun(scale, normV2, new BigDecimal("3"), new BigDecimal("1"));
        CapabilityProfileCurrent prior = CapabilityProfileCurrent.builder()
                .userId(userId).scaleId(scaleId).testId(testId)
                .latestResultId(UUID.randomUUID())
                .stenScore(new BigDecimal("6.0")).normTableVersionId(normV1.getId())
                .scoredAt(LocalDateTime.now().minusDays(30)).nAdministrations(1).build();
        when(capabilityProfileCurrentRepository.findByUserIdAndScaleId(userId, scaleId))
                .thenReturn(Optional.of(prior));

        scoringService.scoreSession(session);

        ArgumentCaptor<CapabilityProfileCurrent> cCap =
                ArgumentCaptor.forClass(CapabilityProfileCurrent.class);
        verify(capabilityProfileCurrentRepository).save(cCap.capture());
        CapabilityProfileCurrent c = cCap.getValue();
        assertThat(c.isNormChanged()).isTrue();
        assertThat(c.getNormTableVersionId()).isEqualTo(normV2.getId());
        assertThat(c.getPrevNormVersionId()).isEqualTo(normV1.getId());
        // Append-only: only ONE new history row is written (prior rows untouched).
        verify(capabilityScoreHistoryRepository, times(1)).save(any());
    }

    @Test
    void restrictedScale_isNotRecorded() {
        PsychometricScale restricted = PsychometricScale.builder()
                .id(scaleId).test(psychTest).name("Manipulativeness")
                .scoreMethod(ScoreMethod.SUM).restricted(true).build();
        setupRun(restricted, normV1, new BigDecimal("3"), new BigDecimal("1"));

        scoringService.scoreSession(session);

        verify(capabilityScoreHistoryRepository, never()).save(any());
        verify(capabilityProfileCurrentRepository, never()).save(any());
    }

    @Test
    void invalidResult_isNotRecorded() {
        // A scale named "Consistency" with sten ≤ 2 → result validity INVALID → no history.
        PsychometricScale consistency = PsychometricScale.builder()
                .id(scaleId).test(psychTest).name("Consistency")
                .scoreMethod(ScoreMethod.SUM).build();
        // mean=10, sd=2 → raw=3 → z=-3.5 → sten clamped to 1 (≤2) → INVALID
        setupRun(consistency, normV1, new BigDecimal("10"), new BigDecimal("2"));

        scoringService.scoreSession(session);

        verify(capabilityScoreHistoryRepository, never()).save(any());
    }

    @Test
    void anonymousSession_isNotRecorded() {
        Form form = Form.builder().id(formId).title("P").build();
        session = ResponseSession.builder().id(sessionId).form(form).user(null).build();
        setupRun(scale, normV1, new BigDecimal("3"), new BigDecimal("1"));

        scoringService.scoreSession(session);

        verify(capabilityScoreHistoryRepository, never()).save(any());
    }
}
