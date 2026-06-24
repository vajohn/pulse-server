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

/**
 * Phase 3 — consolidation gating + accrual in {@link ScoringService}.
 *
 * <p>Scale A is IMMEDIATE (1 item), scale B is CONSOLIDATED (items_required=4). Asserts that a
 * below-predicate CONSOLIDATED scale is NOT scored (no partial STEN), the per-session result is
 * PROVISIONAL, exposure + progress accrue; and that meeting the predicate scores B FINAL with the
 * pinned norm version.
 */
@ExtendWith(MockitoExtension.class)
class ScoringServiceConsolidationTest {

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

    @InjectMocks ScoringService scoringService;

    private UUID formId, testId, sessionId, userId;
    private UUID scaleAId, scaleBId, qA, qB1, qB2, qB3, qB4;
    private ResponseSession session;
    private PsychometricTest psychTest;
    private PsychometricScale scaleA;   // IMMEDIATE
    private PsychometricScale scaleB;   // CONSOLIDATED, 4 items
    private ScoringKeyVersion activeKey;
    private NormTableVersion norm;

    @BeforeEach
    void setUp() {
        formId = UUID.randomUUID();
        testId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
        userId = UUID.randomUUID();
        scaleAId = UUID.randomUUID();
        scaleBId = UUID.randomUUID();
        qA = UUID.randomUUID();
        qB1 = UUID.randomUUID(); qB2 = UUID.randomUUID();
        qB3 = UUID.randomUUID(); qB4 = UUID.randomUUID();

        Form form = Form.builder().id(formId).title("Micro").build();
        User user = User.builder().id(userId).build();
        session = ResponseSession.builder().id(sessionId).form(form).user(user).build();

        psychTest = PsychometricTest.builder().id(testId).form(form).name("P-Test")
                .testType(TestType.PERSONALITY).build();

        scaleA = PsychometricScale.builder().id(scaleAId).test(psychTest).name("Immediate-A")
                .scoreMethod(ScoreMethod.SUM).resultMode(ResultMode.IMMEDIATE).build();
        scaleB = PsychometricScale.builder().id(scaleBId).test(psychTest).name("Consolidated-B")
                .scoreMethod(ScoreMethod.SUM).resultMode(ResultMode.CONSOLIDATED).build();

        activeKey = ScoringKeyVersion.builder().id(UUID.randomUUID()).test(psychTest)
                .version(1).status(ScoringKeyStatus.ACTIVE).build();

        norm = NormTableVersion.builder().id(UUID.randomUUID())
                .normStrategy(NormStrategyType.PARAMETRIC).build();
    }

    private ScoringKeyItem item(UUID id, PsychometricScale scale, UUID questionId) {
        Question q = Question.builder().id(questionId).questionType(QuestionType.SCALE).build();
        return ScoringKeyItem.builder().id(id).scale(scale).question(q)
                .direction(ScoreDirection.FORWARD).weight(BigDecimal.ONE).build();
    }

    private AnswerScale answer(UUID questionId, int value) {
        Question q = Question.builder().id(questionId).build();
        AnswerSubmission sub = AnswerSubmission.builder().id(UUID.randomUUID()).question(q).build();
        return AnswerScale.builder().id(UUID.randomUUID()).submission(sub)
                .value(value).minValue(1).maxValue(5).build();
    }

    @Test
    void consolidatedScale_belowPredicate_isNotScored_resultProvisional() {
        // 5 scoring-key items: A (1), B (4). This session answers A's item + ONE of B's items.
        ScoringKeyItem iA = item(UUID.randomUUID(), scaleA, qA);
        ScoringKeyItem iB1 = item(UUID.randomUUID(), scaleB, qB1);
        ScoringKeyItem iB2 = item(UUID.randomUUID(), scaleB, qB2);
        ScoringKeyItem iB3 = item(UUID.randomUUID(), scaleB, qB3);
        ScoringKeyItem iB4 = item(UUID.randomUUID(), scaleB, qB4);

        when(testRepository.findByFormId(formId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeKey));
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(activeKey.getId()))
                .thenReturn(List.of(iA, iB1, iB2, iB3, iB4));
        // answered this session: A's question + B's first question only
        when(answerScaleRepository.findCurrentBySessionId(sessionId))
                .thenReturn(List.of(answer(qA, 3), answer(qB1, 4)));
        when(answerChoiceRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(answerAdjectiveRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(scoringKeyCorrectAnswerRepository.findByItemIdIn(any())).thenReturn(List.of());
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(scaleA, scaleB));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.of(norm));
        // No existing progress for B → new window opened
        when(scaleProgressRepository.findByUserIdAndTestId(userId, testId)).thenReturn(List.of());
        when(scaleProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // A's norm (so A can be scored)
        lenient().when(normScaleParamRepository.findByNormTable_IdAndScale_Id(any(), eq(scaleAId)))
                .thenReturn(Optional.of(NormScaleParam.builder().normTable(norm).scale(scaleA)
                        .mean(new BigDecimal("3")).sd(new BigDecimal("1"))
                        .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                        .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build()));

        TestResult saved = TestResult.builder().id(UUID.randomUUID())
                .status(TestResultStatus.SCORED).scoringKeyVersion(activeKey).build();
        when(testResultRepository.save(any())).thenReturn(saved);
        lenient().when(competencyScaleWeightRepository.findByScaleIdIn(any())).thenReturn(List.of());
        when(scaleScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scoringService.scoreSession(session);

        // B is NOT scored (no partial STEN) — only A produced a ScaleScore
        ArgumentCaptor<ScaleScore> ssCap = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(ssCap.capture());
        assertThat(ssCap.getAllValues()).anyMatch(ss -> ss.getScale().getId().equals(scaleAId));
        assertThat(ssCap.getAllValues()).noneMatch(ss -> ss.getScale().getId().equals(scaleBId));

        // B's progress accrued and stayed COLLECTING with itemsCollected=1, itemsRequired=4
        ArgumentCaptor<ScaleProgress> spCap = ArgumentCaptor.forClass(ScaleProgress.class);
        verify(scaleProgressRepository, atLeastOnce()).save(spCap.capture());
        ScaleProgress bProgress = spCap.getAllValues().stream()
                .filter(p -> p.getScaleId().equals(scaleBId)).reduce((a, b) -> b).orElseThrow();
        assertThat(bProgress.getState()).isEqualTo(ScaleProgressState.COLLECTING);
        assertThat(bProgress.getItemsCollected()).isEqualTo(1);
        assertThat(bProgress.getItemsRequired()).isEqualTo(4);

        // Result is PROVISIONAL (B still collecting)
        ArgumentCaptor<TestResult> trCap = ArgumentCaptor.forClass(TestResult.class);
        verify(testResultRepository, atLeast(1)).save(trCap.capture());
        assertThat(trCap.getAllValues().get(0).getResultState()).isEqualTo(ResultState.PROVISIONAL);
    }

    @Test
    void consolidatedScale_predicateMet_scoresFinal_withPinnedNorm() {
        // Existing B progress: 3 of 4 collected, norm pinned to `norm`. This session answers the 4th.
        ScoringKeyItem iB1 = item(UUID.randomUUID(), scaleB, qB1);
        ScoringKeyItem iB2 = item(UUID.randomUUID(), scaleB, qB2);
        ScoringKeyItem iB3 = item(UUID.randomUUID(), scaleB, qB3);
        ScoringKeyItem iB4 = item(UUID.randomUUID(), scaleB, qB4);

        UUID windowId = UUID.randomUUID();
        ScaleProgress existing = ScaleProgress.builder()
                .id(UUID.randomUUID()).userId(userId).scaleId(scaleBId).testId(testId)
                .windowId(windowId).normTableVersionId(norm.getId())
                .itemsRequired(4).itemsCollected(3).state(ScaleProgressState.COLLECTING).build();

        when(testRepository.findByFormId(formId)).thenReturn(Optional.of(psychTest));
        when(testResultRepository.findBySessionId(sessionId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeKey));
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(activeKey.getId()))
                .thenReturn(List.of(iB1, iB2, iB3, iB4));
        // This session answers the 4th item only (qB4)
        when(answerScaleRepository.findCurrentBySessionId(sessionId))
                .thenReturn(List.of(answer(qB4, 3)));
        when(answerChoiceRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(answerAdjectiveRepository.findCurrentBySessionId(sessionId)).thenReturn(List.of());
        lenient().when(scoringKeyCorrectAnswerRepository.findByItemIdIn(any())).thenReturn(List.of());
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(scaleB));
        // Live norm differs from the pinned one — must NOT be used for B
        NormTableVersion liveNorm = NormTableVersion.builder().id(UUID.randomUUID())
                .normStrategy(NormStrategyType.PARAMETRIC).build();
        when(normTableVersionRepository.findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED))
                .thenReturn(Optional.of(liveNorm));
        when(scaleProgressRepository.findByUserIdAndTestId(userId, testId))
                .thenReturn(new ArrayList<>(List.of(existing)));
        when(scaleProgressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Pinned norm is loaded by id
        when(normTableVersionRepository.findById(norm.getId())).thenReturn(Optional.of(norm));
        // B's params live on the PINNED norm
        when(normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), scaleBId))
                .thenReturn(Optional.of(NormScaleParam.builder().normTable(norm).scale(scaleB)
                        .mean(new BigDecimal("4")).sd(new BigDecimal("2"))
                        .tFactor(new BigDecimal("10")).tOffset(new BigDecimal("50"))
                        .tClipLo(new BigDecimal("10")).tClipHi(new BigDecimal("120")).build()));

        TestResult saved = TestResult.builder().id(UUID.randomUUID())
                .status(TestResultStatus.SCORED).scoringKeyVersion(activeKey).build();
        when(testResultRepository.save(any())).thenReturn(saved);
        lenient().when(competencyScaleWeightRepository.findByScaleIdIn(any())).thenReturn(List.of());
        when(scaleScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        scoringService.scoreSession(session);

        // B is now scored FINAL (predicate met) with a non-null STEN from the PINNED norm
        ArgumentCaptor<ScaleScore> ssCap = ArgumentCaptor.forClass(ScaleScore.class);
        verify(scaleScoreRepository, atLeastOnce()).save(ssCap.capture());
        ScaleScore bScore = ssCap.getAllValues().stream()
                .filter(ss -> ss.getScale().getId().equals(scaleBId)).findFirst().orElseThrow();
        assertThat(bScore.getStenScore()).isNotNull();

        // The pinned norm was loaded; the live norm was never consulted for B's params
        verify(normTableVersionRepository).findById(norm.getId());
        verify(normScaleParamRepository).findByNormTable_IdAndScale_Id(norm.getId(), scaleBId);
        verify(normScaleParamRepository, never())
                .findByNormTable_IdAndScale_Id(eq(liveNorm.getId()), eq(scaleBId));

        // Progress flipped to CONSOLIDATED with consolidatedAt set
        ArgumentCaptor<ScaleProgress> spCap = ArgumentCaptor.forClass(ScaleProgress.class);
        verify(scaleProgressRepository, atLeastOnce()).save(spCap.capture());
        ScaleProgress consolidated = spCap.getAllValues().stream()
                .filter(p -> p.getScaleId().equals(scaleBId))
                .filter(p -> p.getState() == ScaleProgressState.CONSOLIDATED)
                .reduce((a, b) -> b).orElseThrow();
        assertThat(consolidated.getConsolidatedAt()).isNotNull();
        assertThat(consolidated.getItemsCollected()).isGreaterThanOrEqualTo(4);

        // Result is FINAL (no scale left collecting)
        ArgumentCaptor<TestResult> trCap = ArgumentCaptor.forClass(TestResult.class);
        verify(testResultRepository, atLeast(1)).save(trCap.capture());
        assertThat(trCap.getAllValues().get(0).getResultState()).isEqualTo(ResultState.FINAL);
    }
}
