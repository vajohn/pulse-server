package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CreatePsychometricTestRequest;
import com.edge.pulse.data.dto.psychometric.NormEntryRequest;
import com.edge.pulse.data.dto.psychometric.PsychometricTestAnalyticsDto;
import com.edge.pulse.data.dto.psychometric.ScoringKeyItemRequest;
import com.edge.pulse.data.dto.psychometric.UpdatePsychometricTestRequest;
import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.mappers.FormMapper;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.NormEntryRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.PsychometricAnalyticsMvRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestResultRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SurveyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PsychometricAdminServiceTest {

    @Mock private TestResultRepository testResultRepository;
    @Mock private UserRepository userRepository;
    @Mock private FormRepository formRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private PsychometricTestRepository testRepository;
    @Mock private PsychometricScaleRepository scaleRepository;
    @Mock private PsychometricAnalyticsMvRepository psychometricMvRepo;
    @Mock private ScoringService scoringService;
    @Mock private AuditService auditService;
    @Mock private CandidatePsychometricService candidateService;
    @Mock private ResultVisibilityPolicyRepository visibilityPolicyRepository;
    @Mock private SurveyService surveyService;
    @Mock private FormMapper formMapper;
    @Mock private ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock private ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock private NormTableVersionRepository normTableVersionRepository;
    @Mock private NormEntryRepository normEntryRepository;
    @Mock private CandidateAnswerRepository candidateAnswerRepository;

    @InjectMocks
    private PsychometricAdminService adminService;

    // ── getTestAnalytics ──────────────────────────────────────────────────────

    @Test
    void getTestAnalytics_mapsRowsToDto_whenMvHasData() {
        UUID testId  = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        when(testRepository.existsById(testId)).thenReturn(true);

        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 3, 1, 10, 0));
        // Summary row: [total, pending, scored, reviewed, flagged, lastScoredAt, avgFocus]
        when(psychometricMvRepo.findTestSummary(testId)).thenReturn(new Object[]{
                10L, 2L, 6L, 1L, 1L, ts, 0.5
        });

        // Scale row: [scaleId, resultCount, avgRaw, avgSten, avgPct, stddev, sten_1..sten_10]
        when(psychometricMvRepo.findScaleStatsByTest(testId, 5)).thenReturn(List.<Object[]>of(
                new Object[]{
                        scaleId, 6L, 3.5, 6.2, 70.1, 1.2,
                        0L, 0L, 1L, 2L, 2L, 1L, 0L, 0L, 0L, 0L
                }
        ));

        PsychometricTestAnalyticsDto result = adminService.getTestAnalytics(testId);

        assertThat(result.testId()).isEqualTo(testId);
        assertThat(result.totalResults()).isEqualTo(10L);
        assertThat(result.pendingCount()).isEqualTo(2L);
        assertThat(result.scoredCount()).isEqualTo(6L);
        assertThat(result.reviewedCount()).isEqualTo(1L);
        assertThat(result.flaggedCount()).isEqualTo(1L);
        assertThat(result.lastScoredAt()).isEqualTo(LocalDateTime.of(2026, 3, 1, 10, 0));
        assertThat(result.avgFocusLossCount()).isEqualTo(0.5);

        assertThat(result.scaleStats()).hasSize(1);
        PsychometricTestAnalyticsDto.ScaleAnalyticsDto scale = result.scaleStats().getFirst();
        assertThat(scale.scaleId()).isEqualTo(scaleId);
        assertThat(scale.resultCount()).isEqualTo(6L);
        assertThat(scale.avgRawScore()).isEqualTo(3.5);
        assertThat(scale.avgSten()).isEqualTo(6.2);
        assertThat(scale.avgPercentile()).isEqualTo(70.1);
        assertThat(scale.stddevRawScore()).isEqualTo(1.2);

        // Verify the 16-column index mapping: row[6..15] → stenHistogram[0..9]
        assertThat(scale.stenHistogram()).hasSize(10);
        assertThat(scale.stenHistogram()[0]).isEqualTo(0L);  // sten-1
        assertThat(scale.stenHistogram()[2]).isEqualTo(1L);  // sten-3
        assertThat(scale.stenHistogram()[3]).isEqualTo(2L);  // sten-4
        assertThat(scale.stenHistogram()[4]).isEqualTo(2L);  // sten-5
        assertThat(scale.stenHistogram()[5]).isEqualTo(1L);  // sten-6
        assertThat(scale.stenHistogram()[9]).isEqualTo(0L);  // sten-10
    }

    @Test
    void getTestAnalytics_returnsZeroesAndNulls_whenNoMvData() {
        UUID testId = UUID.randomUUID();

        when(testRepository.existsById(testId)).thenReturn(true);
        when(psychometricMvRepo.findTestSummary(testId)).thenReturn(null);
        when(psychometricMvRepo.findScaleStatsByTest(testId, 5)).thenReturn(List.of());

        PsychometricTestAnalyticsDto result = adminService.getTestAnalytics(testId);

        assertThat(result.testId()).isEqualTo(testId);
        assertThat(result.totalResults()).isZero();
        assertThat(result.pendingCount()).isZero();
        assertThat(result.scoredCount()).isZero();
        assertThat(result.lastScoredAt()).isNull();
        assertThat(result.avgFocusLossCount()).isNull();
        assertThat(result.scaleStats()).isEmpty();
    }

    @Test
    void getTestAnalytics_throws404_whenTestNotFound() {
        UUID testId = UUID.randomUUID();

        when(testRepository.existsById(testId)).thenReturn(false);

        assertThatThrownBy(() -> adminService.getTestAnalytics(testId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getTestAnalytics_handlesNullScaleStatFields_gracefully() {
        UUID testId  = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        when(testRepository.existsById(testId)).thenReturn(true);
        when(psychometricMvRepo.findTestSummary(testId)).thenReturn(new Object[]{
                8L, 3L, 5L, 0L, 0L,
                null,  // lastScoredAt = null
                null   // avgFocusLoss = null
        });
        // Scale with null optional stats (no norm table → no sten/percentile)
        when(psychometricMvRepo.findScaleStatsByTest(testId, 5)).thenReturn(List.<Object[]>of(
                new Object[]{
                        scaleId, 5L,
                        4.1,    // avgRaw present
                        null,   // avgSten absent (no norm)
                        null,   // avgPercentile absent
                        null,   // stddev absent
                        0L, 0L, 0L, 0L, 5L, 0L, 0L, 0L, 0L, 0L
                }
        ));

        PsychometricTestAnalyticsDto result = adminService.getTestAnalytics(testId);

        assertThat(result.lastScoredAt()).isNull();
        assertThat(result.avgFocusLossCount()).isNull();

        PsychometricTestAnalyticsDto.ScaleAnalyticsDto scale = result.scaleStats().getFirst();
        assertThat(scale.avgSten()).isNull();
        assertThat(scale.avgPercentile()).isNull();
        assertThat(scale.stddevRawScore()).isNull();
        assertThat(scale.avgRawScore()).isEqualTo(4.1);
        assertThat(scale.stenHistogram()[4]).isEqualTo(5L);  // sten-5
    }

    // ── TestTypeCapabilities validation ──────────────────────────────────────

    @Test
    void createTest_cognitive_requiresPositiveTimeLimitSecs() {
        var req = new CreatePsychometricTestRequest(
                "Spatial IQ", null, null,
                TestType.COGNITIVE, null  // missing time limit
        );
        assertThatThrownBy(() -> adminService.createTest(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createTest_cognitive_rejectsZeroTimeLimitSecs() {
        var req = new CreatePsychometricTestRequest(
                "Spatial IQ", null, null,
                TestType.COGNITIVE, 0  // zero is not positive
        );
        assertThatThrownBy(() -> adminService.createTest(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createTest_personality_rejectsTimeLimitSecs() {
        var req = new CreatePsychometricTestRequest(
                "Big-Five", null, null,
                TestType.PERSONALITY, 1800  // personality must be untimed
        );
        assertThatThrownBy(() -> adminService.createTest(req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── activateTest ──────────────────────────────────────────────────────────

    @Test
    void activateTest_draft_setsActiveAndLogsAudit() {
        UUID testId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Form form = Form.builder().id(UUID.randomUUID()).build();
        PsychometricTest test = PsychometricTest.builder()
                .id(testId)
                .status(TestStatus.DRAFT)
                .testType(TestType.PERSONALITY)
                .form(form)
                .build();
        when(testRepository.findById(testId)).thenReturn(java.util.Optional.of(test));
        when(testRepository.save(test)).thenReturn(test);
        when(scaleRepository.countByTestId(testId)).thenReturn(1);
        when(formRepository.countActiveQuestionsByFormId(any())).thenReturn(2L);
        when(auditService.buildDetail(any(), any())).thenReturn("{}");

        var result = adminService.activateTest(testId, userId);

        assertThat(test.getStatus()).isEqualTo(TestStatus.ACTIVE);
        verify(testRepository).save(test);
        verify(auditService).logAction(userId, "PSYCHOMETRIC_TEST_ACTIVATED",
                "PsychometricTest", testId, "{}", null);
    }

    @Test
    void activateTest_alreadyActive_setsActiveAgain_idempotent() {
        UUID testId = UUID.randomUUID();
        Form form = Form.builder().id(UUID.randomUUID()).build();
        PsychometricTest test = PsychometricTest.builder()
                .id(testId)
                .status(TestStatus.ACTIVE)
                .testType(TestType.PERSONALITY)
                .form(form)
                .build();
        when(testRepository.findById(testId)).thenReturn(java.util.Optional.of(test));
        when(testRepository.save(test)).thenReturn(test);
        when(scaleRepository.countByTestId(testId)).thenReturn(1);
        when(formRepository.countActiveQuestionsByFormId(any())).thenReturn(1L);
        when(auditService.buildDetail(any(), any())).thenReturn("{}");

        // Should not throw — idempotent re-activation is allowed
        var result = adminService.activateTest(testId, UUID.randomUUID());
        assertThat(result).isNotNull();
    }

    @Test
    void activateTest_retired_throws409() {
        UUID testId = UUID.randomUUID();
        PsychometricTest test = PsychometricTest.builder()
                .id(testId)
                .status(TestStatus.RETIRED)
                .build();
        when(testRepository.findById(testId)).thenReturn(java.util.Optional.of(test));

        assertThatThrownBy(() -> adminService.activateTest(testId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void activateTest_notFound_throws404() {
        UUID testId = UUID.randomUUID();
        when(testRepository.findById(testId)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> adminService.activateTest(testId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateTest_rejectsTimeLimitOnPersonalityTest() {
        UUID testId = UUID.randomUUID();
        PsychometricTest test = PsychometricTest.builder()
                .testType(TestType.PERSONALITY)
                .build();
        when(testRepository.findById(testId)).thenReturn(java.util.Optional.of(test));

        var req = new UpdatePsychometricTestRequest(null, null, null, 900);
        assertThatThrownBy(() -> adminService.updateTest(testId, req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── saveScoringKey ────────────────────────────────────────────────────────

    @Test
    void saveScoringKey_deprecatesOldKeyAndCreatesNew() {
        UUID testId    = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID scaleId   = UUID.randomUUID();

        PsychometricTest test = PsychometricTest.builder().id(testId).build();
        User user = User.builder().id(userId).build();

        Question question = Question.builder()
                .id(questionId).questionType(QuestionType.CHOICE_SINGLE).body("What is 2+2?")
                .build();
        PsychometricScale scale = PsychometricScale.builder()
                .id(scaleId).name("Verbal").test(test).build();

        ScoringKeyVersion savedKey = ScoringKeyVersion.builder()
                .id(UUID.randomUUID()).test(test).version(1).label("UI v1")
                .status(ScoringKeyStatus.ACTIVE).publishedBy(user).build();
        ScoringKeyItem savedItem = ScoringKeyItem.builder()
                .question(question).scale(scale).build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questionRepository.findAllById(any())).thenReturn(List.of(question));
        when(scaleRepository.findAllById(any())).thenReturn(List.of(scale));
        when(candidateAnswerRepository.findAllById(any())).thenReturn(List.of());
        when(scoringKeyVersionRepository.deprecateActiveKeysByTestId(testId)).thenReturn(1);
        when(scoringKeyVersionRepository.findMaxVersionByTestId(testId)).thenReturn(Optional.empty());
        when(scoringKeyVersionRepository.save(any())).thenReturn(savedKey);
        when(scoringKeyItemRepository.save(any())).thenReturn(savedItem);
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        var request = new ScoringKeyItemRequest(questionId, scaleId, null, null, null, false);
        var result = adminService.saveScoringKey(testId, List.of(request), userId);

        assertThat(result).hasSize(1);
        verify(scoringKeyVersionRepository).deprecateActiveKeysByTestId(testId);
        verify(scoringKeyVersionRepository).save(any());
        verify(scoringKeyItemRepository).save(any());
        verify(auditService).logAction(eq(userId), eq("SCORING_KEY_SAVE"),
                eq("ScoringKeyVersion"), any(), any(), isNull());
    }

    @Test
    void saveScoringKey_unknownQuestionId_throwsBadRequest() {
        UUID testId     = UUID.randomUUID();
        UUID userId     = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID scaleId    = UUID.randomUUID();

        PsychometricTest test = PsychometricTest.builder().id(testId).build();
        User user = User.builder().id(userId).build();
        PsychometricScale scale = PsychometricScale.builder()
                .id(scaleId).name("Verbal").test(test).build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // Question NOT in the returned list — simulates unknown questionId
        when(questionRepository.findAllById(any())).thenReturn(List.of());
        when(scaleRepository.findAllById(any())).thenReturn(List.of(scale));
        when(candidateAnswerRepository.findAllById(any())).thenReturn(List.of());

        var request = new ScoringKeyItemRequest(questionId, scaleId, null, null, null, false);
        assertThatThrownBy(() -> adminService.saveScoringKey(testId, List.of(request), userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(scoringKeyVersionRepository, never()).deprecateActiveKeysByTestId(any());
    }

    @Test
    void saveScoringKey_scaleFromDifferentTest_throwsBadRequest() {
        UUID testId     = UUID.randomUUID();
        UUID otherTestId = UUID.randomUUID();
        UUID userId     = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        UUID scaleId    = UUID.randomUUID();

        PsychometricTest test = PsychometricTest.builder().id(testId).build();
        User user = User.builder().id(userId).build();
        Question question = Question.builder()
                .id(questionId).questionType(QuestionType.SCALE).body("Rate your confidence").build();
        // Scale belongs to a DIFFERENT test
        PsychometricScale wrongScale = PsychometricScale.builder()
                .id(scaleId).name("Other").test(PsychometricTest.builder().id(otherTestId).build())
                .build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questionRepository.findAllById(any())).thenReturn(List.of(question));
        when(scaleRepository.findAllById(any())).thenReturn(List.of(wrongScale));
        when(candidateAnswerRepository.findAllById(any())).thenReturn(List.of());

        var request = new ScoringKeyItemRequest(questionId, scaleId, null, null, null, false);
        assertThatThrownBy(() -> adminService.saveScoringKey(testId, List.of(request), userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(scoringKeyVersionRepository, never()).deprecateActiveKeysByTestId(any());
    }

    @Test
    void saveScoringKey_emptyList_createsEmptyVersion() {
        UUID testId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PsychometricTest test = PsychometricTest.builder().id(testId).build();
        User user = User.builder().id(userId).build();
        ScoringKeyVersion savedKey = ScoringKeyVersion.builder()
                .id(UUID.randomUUID()).test(test).version(1).label("UI v1")
                .status(ScoringKeyStatus.ACTIVE).publishedBy(user).build();

        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(questionRepository.findAllById(any())).thenReturn(List.of());
        when(scaleRepository.findAllById(any())).thenReturn(List.of());
        when(candidateAnswerRepository.findAllById(any())).thenReturn(List.of());
        when(scoringKeyVersionRepository.deprecateActiveKeysByTestId(testId)).thenReturn(0);
        when(scoringKeyVersionRepository.findMaxVersionByTestId(testId)).thenReturn(Optional.of(3));
        when(scoringKeyVersionRepository.save(any())).thenReturn(savedKey);
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        var result = adminService.saveScoringKey(testId, List.of(), userId);

        assertThat(result).isEmpty();
        verify(scoringKeyVersionRepository).deprecateActiveKeysByTestId(testId);
        verify(scoringKeyItemRepository, never()).save(any());
    }

    // ── saveNormTable ─────────────────────────────────────────────────────────

    @Test
    void saveNormTable_invalidScaleId_throwsBadRequest() {
        UUID testId  = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        PsychometricTest test = PsychometricTest.builder().id(testId).build();
        User user = User.builder().id(userId).build();

        // Scale NOT in the returned list — simulates unknown / wrong-test scaleId
        when(testRepository.findById(testId)).thenReturn(Optional.of(test));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(scaleRepository.findAllById(any())).thenReturn(List.of());

        var entry = new NormEntryRequest(scaleId, new BigDecimal("5"),
                BigDecimal.valueOf(10), BigDecimal.valueOf(15), null, null);
        assertThatThrownBy(() -> adminService.saveNormTable(testId, List.of(entry), userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));

        verify(normTableVersionRepository, never()).deprecateValidatedNormsByTestId(any());
    }
}
