package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.dto.UpdateQuestionRequest;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.TestTypeCapabilityDto;
import com.edge.pulse.data.dto.psychometric.NormEntryDto;
import com.edge.pulse.data.dto.psychometric.NormEntryRequest;
import com.edge.pulse.data.dto.psychometric.ScoringKeyItemDto;
import com.edge.pulse.data.dto.psychometric.ScoringKeyItemRequest;
import com.edge.pulse.data.dto.psychometric.CreatePsychometricTestRequest;
import com.edge.pulse.data.dto.psychometric.CreateScaleRequest;
import com.edge.pulse.data.dto.psychometric.PsychometricScaleDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestAnalyticsDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.data.dto.psychometric.UpdatePsychometricTestRequest;
import com.edge.pulse.data.dto.psychometric.UpdateScaleRequest;
import com.edge.pulse.data.dto.psychometric.UpsertVisibilityPolicyRequest;
import com.edge.pulse.data.dto.psychometric.VisibilityPolicyDto;
import com.edge.pulse.data.dto.psychometric.imports.NormScaleParamRequest;
import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.data.enums.FormType;
import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ResultAudience;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.enums.ScoreMethod;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestTypeCapabilities;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.NormEntry;
import com.edge.pulse.data.models.psychometric.NormScaleParam;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricInstrument;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ResultVisibilityPolicy;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.models.psychometric.TestResult;
import com.edge.pulse.repositories.psychometric.NormEntryRepository;
import com.edge.pulse.repositories.psychometric.NormScaleParamRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.PsychometricAnalyticsMvRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.TestResultRepository;
import com.edge.pulse.services.AnalyticsConstants;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SurveyService;
import com.edge.pulse.mappers.FormMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PsychometricAdminService {

    private static final int MIN_RESPONDENTS = AnalyticsConstants.MIN_RESPONDENTS;

    private final TestResultRepository testResultRepository;
    private final UserRepository userRepository;
    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final PsychometricTestRepository testRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final PsychometricAnalyticsMvRepository psychometricMvRepo;
    private final ScoringService scoringService;
    private final AuditService auditService;
    private final CandidatePsychometricService candidateService;
    private final ResultVisibilityPolicyRepository visibilityPolicyRepository;
    private final SurveyService surveyService;
    private final FormMapper formMapper;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final ScoringKeyItemRepository scoringKeyItemRepository;
    private final NormTableVersionRepository normTableVersionRepository;
    private final NormEntryRepository normEntryRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final NormScaleParamRepository normScaleParamRepository;
    private final InstrumentService instrumentService;
    private final com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository testApprovalRequestRepository;
    private final com.edge.pulse.mappers.psychometric.TestApprovalMapper testApprovalMapper;

    // ── Type catalog ──────────────────────────────────────────────────────────

    /**
     * Returns the enriched capability catalog for all test types.
     * Single source of truth for the dashboard's type dropdown and tooltip.
     */
    @Transactional(readOnly = true)
    public List<TestTypeCapabilityDto> listTestTypeCapabilities() {
        return Arrays.stream(TestType.values())
                .map(type -> {
                    TestTypeCapabilities c = TestTypeCapabilities.of(type);
                    return new TestTypeCapabilityDto(
                            type.name(),
                            c.displayLabel,
                            c.description,
                            c.measures,
                            c.exampleInstruments,
                            c.timeLimitRequired,
                            c.timeLimitVisible,
                            c.allowedQuestionTypes.stream().map(Enum::name).sorted().toList());
                })
                .toList();
    }

    // ── Test CRUD ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PsychometricTestDto> listTests(TestStatus status, Pageable pageable) {
        Page<PsychometricTest> tests = status != null
                ? testRepository.findByStatusWithForm(status, pageable)
                : testRepository.findAllWithForm(pageable);

        // Batch scale counts — one query for all tests in the page
        List<UUID> testIds = tests.stream().map(PsychometricTest::getId).toList();
        Map<UUID, Long> scaleCountMap = testIds.isEmpty() ? Map.of() :
                scaleRepository.countByTestIdIn(testIds).stream()
                        .collect(Collectors.toMap(
                                row -> (UUID) row[0],
                                row -> (Long) row[1]));

        // Batch active question counts (date-window filtered) — one query for all forms in the page.
        // Uses countActiveByFormIds so the displayed count matches the assignment guard.
        List<UUID> formIds = tests.stream().map(t -> t.getForm().getId()).toList();
        Map<UUID, Long> questionCountMap = formIds.isEmpty() ? Map.of() :
                questionRepository.countActiveByFormIds(formIds).stream()
                        .collect(Collectors.toMap(
                                row -> (UUID) row[0],
                                row -> (Long) row[1]));

        return tests.map(t -> toTestDto(t,
                scaleCountMap.getOrDefault(t.getId(), 0L).intValue(),
                questionCountMap.getOrDefault(t.getForm().getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public PsychometricTestDto getTest(UUID testId) {
        PsychometricTest test = testRepository.findByIdWithForm(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int questionCount = (int) formRepository.countActiveQuestionsByFormId(test.getForm().getId());
        return toTestDto(test, countScales(testId), questionCount);
    }

    /**
     * Creates a Form (formType=PSYCHOMETRIC) and a PsychometricTest atomically.
     *
     * <p>Validates {@code timeLimitSecs} against the capability profile for the
     * requested test type: COGNITIVE requires a positive time limit; PERSONALITY
     * must not have one.
     */
    @Transactional
    public PsychometricTestDto createTest(CreatePsychometricTestRequest req, UUID createdById) {
        validateTimeLimitForType(req.testType(), req.timeLimitSecs());

        Form form = Form.builder()
                .title(req.name())
                .description(req.description())
                .formType(FormType.PSYCHOMETRIC)
                .anonWindowMinutes(0)
                .build();
        formRepository.save(form);

        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        PsychometricInstrument instrument = instrumentService.resolveOrCreate(req.instrument());

        PsychometricTest test = PsychometricTest.builder()
                .form(form)
                .name(req.name())
                .description(req.description())
                .instructions(req.instructions())
                .testType(req.testType())
                .timeLimitSecs(req.timeLimitSecs())
                .createdBy(creator)
                .instrument(instrument)
                .status(TestStatus.DRAFT)
                .version(1)
                .build();
        testRepository.save(test);

        auditService.logAction(createdById, "PSYCHOMETRIC_TEST_CREATED", "PsychometricTest",
                test.getId(), auditService.buildDetail("name", req.name(), "testType", req.testType()), null);

        return toTestDto(test, 0, 0);
    }

    @Transactional
    public PsychometricTestDto updateTest(UUID testId, UpdatePsychometricTestRequest req, UUID updatedById) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (req.name() != null)         test.setName(req.name());
        if (req.description() != null)  test.setDescription(req.description());
        if (req.instructions() != null) test.setInstructions(req.instructions());
        if (req.timeLimitSecs() != null) {
            // timeLimitSecs is scoring-affecting — block on ACTIVE
            assertEditableScoring(test);
            validateTimeLimitForType(test.getTestType(), req.timeLimitSecs());
            test.setTimeLimitSecs(req.timeLimitSecs());
        }
        if (req.instrument() != null) {
            test.setInstrument(instrumentService.resolveOrCreate(req.instrument()));
        }

        testRepository.save(test);
        auditService.logAction(updatedById, "PSYCHOMETRIC_TEST_UPDATED", "PsychometricTest",
                testId, auditService.buildDetail("name", test.getName()), null);

        int questionCount = (int) formRepository.countActiveQuestionsByFormId(test.getForm().getId());
        return toTestDto(test, countScales(testId), questionCount);
    }

    @Transactional
    public void archiveTest(UUID testId, UUID archivedById) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        test.setStatus(TestStatus.RETIRED);
        testRepository.save(test);

        auditService.logAction(archivedById, "PSYCHOMETRIC_TEST_ARCHIVED", "PsychometricTest",
                testId, auditService.buildDetail("status", "RETIRED"), null);
    }

    // ── Scale CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PsychometricScaleDto> listScales(UUID testId) {
        // Verify test exists
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return scaleRepository.findByTestIdOrdered(testId).stream()
                .map(this::toScaleDto)
                .toList();
    }

    @Transactional
    public PsychometricScaleDto createScale(UUID testId, CreateScaleRequest req, UUID createdById) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);

        PsychometricScale parentScale = null;
        if (req.parentScaleId() != null) {
            parentScale = scaleRepository.findById(req.parentScaleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Parent scale not found: " + req.parentScaleId()));
        }

        PsychometricScale scale = PsychometricScale.builder()
                .test(test)
                .parentScale(parentScale)
                .name(req.name())
                .description(req.description())
                .scoreMethod(parseScoreMethod(req.scoreMethod()))
                .displayOrder(req.displayOrder())
                .compositeMethod(req.compositeMethod())
                .compositeBasis(req.compositeBasis())
                .compositeRoundingScale(req.compositeRoundingScale())
                .restricted(req.restricted())
                .build();
        scaleRepository.save(scale);

        auditService.logAction(createdById, "PSYCHOMETRIC_SCALE_CREATED", "PsychometricScale",
                scale.getId(), auditService.buildDetail("name", req.name(), "testId", testId), null);

        return toScaleDto(scale);
    }

    @Transactional
    public PsychometricScaleDto updateScale(UUID testId, UUID scaleId,
                                            UpdateScaleRequest req, UUID updatedById) {
        PsychometricScale scale = scaleRepository.findById(scaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(scale.getTest());

        if (req.name() != null)       scale.setName(req.name());
        if (req.description() != null) scale.setDescription(req.description());
        if (req.scoreMethod() != null) scale.setScoreMethod(parseScoreMethod(req.scoreMethod()));
        if (req.displayOrder() != null) scale.setDisplayOrder(req.displayOrder());
        if (req.parentScaleId() != null) {
            PsychometricScale parent = scaleRepository.findById(req.parentScaleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Parent scale not found: " + req.parentScaleId()));
            scale.setParentScale(parent);
        }
        if (req.compositeMethod() != null) scale.setCompositeMethod(req.compositeMethod());
        if (req.compositeBasis() != null)  scale.setCompositeBasis(req.compositeBasis());
        if (req.compositeRoundingScale() != null) scale.setCompositeRoundingScale(req.compositeRoundingScale());

        scaleRepository.save(scale);
        auditService.logAction(updatedById, "PSYCHOMETRIC_SCALE_UPDATED", "PsychometricScale",
                scaleId, auditService.buildDetail("name", scale.getName()), null);

        return toScaleDto(scale);
    }

    @Transactional
    public void deleteScale(UUID testId, UUID scaleId, UUID deletedById) {
        PsychometricScale scale = scaleRepository.findById(scaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(scale.getTest());
        scaleRepository.deleteById(scaleId);
        auditService.logAction(deletedById, "PSYCHOMETRIC_SCALE_DELETED", "PsychometricScale",
                scaleId, auditService.buildDetail("testId", testId), null);
    }

    // ── Result management ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TestResultSummaryDto> listResults(UUID testId, TestResultStatus status,
                                                  int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<TestResult> results = status != null
                ? testResultRepository.findByTestIdAndStatus(testId, status, pageable)
                : testResultRepository.findByTestId(testId, pageable);
        return results.map(this::toSummary);
    }

    @Transactional
    public TestResultSummaryDto reviewResult(UUID resultId, UUID reviewerId,
                                             TestResultStatus newStatus, String notes) {
        if (newStatus != TestResultStatus.REVIEWED && newStatus != TestResultStatus.FLAGGED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Review status must be REVIEWED or FLAGGED");
        }
        // findByIdWithSessionAndTest JOIN FETCHes test + session + user, so
        // toSummary()'s access to r.getTest().getName() does not trigger a lazy load.
        TestResult result = testResultRepository.findByIdWithSessionAndTest(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        result.setStatus(newStatus);
        result.setReviewedBy(reviewer);
        result.setReviewedAt(LocalDateTime.now());
        result.setReviewNotes(notes);
        testResultRepository.save(result);

        auditService.logAction(reviewerId, "TEST_RESULT_REVIEW", "TestResult", resultId,
                auditService.buildDetail("status", newStatus, "notes", notes), null);

        return toSummary(result);
    }

    /** HR_ADMIN result detail — applies HR_ADMIN visibility policy. */
    @Transactional(readOnly = true)
    public CandidateTestResultDetailsDto getAdminResultDetail(UUID resultId) {
        return candidateService.toResultDetailsForAudience(resultId, ResultAudience.HR_ADMIN);
    }

    // ── Visibility policy management ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VisibilityPolicyDto> listVisibilityPolicies(UUID testId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return visibilityPolicyRepository.findByTestId(testId).stream()
                .map(this::toPolicyDto)
                .toList();
    }

    @Transactional
    public VisibilityPolicyDto upsertVisibilityPolicy(UUID testId, UpsertVisibilityPolicyRequest req, UUID updatedById) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        ResultVisibilityPolicy policy = visibilityPolicyRepository
                .findByTestIdAndAudience(testId, req.audience())
                .orElseGet(() -> ResultVisibilityPolicy.builder().test(test).audience(req.audience()).build());

        policy.setShowRawScore(req.showRawScore());
        policy.setShowStenProfile(req.showStenProfile());
        policy.setShowPercentile(req.showPercentile());
        policy.setShowScaleBreakdown(req.showScaleBreakdown());
        policy.setShowCompetencyMap(req.showCompetencyMap());
        policy.setShowPassFailOnly(req.showPassFailOnly());

        policy = visibilityPolicyRepository.save(policy);
        auditService.logAction(updatedById, "VISIBILITY_POLICY_UPDATED", "PsychometricTest",
                testId, auditService.buildDetail("audience", req.audience().name()), null);
        return toPolicyDto(policy);
    }

    private VisibilityPolicyDto toPolicyDto(ResultVisibilityPolicy p) {
        return new VisibilityPolicyDto(p.getId(), p.getAudience(),
                p.isShowRawScore(), p.isShowStenProfile(), p.isShowPercentile(),
                p.isShowScaleBreakdown(), p.isShowCompetencyMap(), p.isShowPassFailOnly());
    }

    @Transactional
    public void rescoreResult(UUID resultId, UUID requestedById) {
        testResultRepository.findById(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        scoringService.rescoreResult(resultId);

        auditService.logAction(requestedById, "TEST_RESULT_RESCORE", "TestResult", resultId,
                auditService.buildDetail("action", "rescore"), null);
    }

    // ── Question CRUD (psychometric-gated) ───────────────────────────────────

    /**
     * Returns all questions associated with the test's underlying form.
     */
    @Transactional(readOnly = true)
    public List<QuestionDto> listQuestions(UUID testId) {
        PsychometricTest test = testRepository.findByIdWithForm(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return surveyService.getAllQuestions(test.getForm().getId())
                .stream()
                .map(formMapper::toQuestionDto)
                .toList();
    }

    /**
     * Adds a question to the psychometric test's underlying form.
     *
     * <p>Validates that the requested {@link QuestionType} is in the capability
     * set for this test's type before delegating to {@link SurveyService}.
     */
    @Transactional
    public QuestionDto addQuestion(UUID testId, AddQuestionRequest req, UUID createdById) {
        PsychometricTest test = testRepository.findByIdWithForm(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);

        // Validate question type against test type capabilities.
        TestTypeCapabilities caps = TestTypeCapabilities.of(test.getTestType());
        if (caps.allowedQuestionTypes.isEmpty()) {
            // Derived types (e.g. COMPETENCY) have no items of their own.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Competency scores are derived from other tests' scales — "
                    + "a competency test has no items of its own.");
        }
        if (!caps.allowedQuestionTypes.contains(req.questionType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question type " + req.questionType() + " is not allowed for "
                    + test.getTestType() + " tests. Allowed: " + caps.allowedQuestionTypes);
        }

        com.edge.pulse.data.models.Question question =
                surveyService.addQuestion(test.getForm().getId(), req);

        auditService.logAction(createdById, "PSYCHOMETRIC_QUESTION_ADDED", "PsychometricTest",
                testId, auditService.buildDetail("questionId", question.getId(),
                        "type", req.questionType()), null);

        return formMapper.toQuestionDto(question);
    }

    /**
     * Updates an existing question on the psychometric test's form.
     */
    @Transactional
    public QuestionDto updateQuestion(UUID testId, UUID questionId,
                                      UpdateQuestionRequest req, UUID updatedById) {
        PsychometricTest test = testRepository.findByIdWithForm(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);

        com.edge.pulse.data.models.Question question =
                surveyService.updateQuestion(test.getForm().getId(), questionId, req);

        auditService.logAction(updatedById, "PSYCHOMETRIC_QUESTION_UPDATED", "Question",
                questionId, auditService.buildDetail("testId", testId), null);

        return formMapper.toQuestionDto(question);
    }

    /**
     * Soft-deletes (expires) a question from the psychometric test.
     */
    @Transactional
    public void deleteQuestion(UUID testId, UUID questionId, UUID deletedById) {
        PsychometricTest test = testRepository.findByIdWithForm(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);

        surveyService.expireQuestion(test.getForm().getId(), questionId);

        auditService.logAction(deletedById, "PSYCHOMETRIC_QUESTION_DELETED", "Question",
                questionId, auditService.buildDetail("testId", testId), null);
    }

    // ── Analytics (materialized views) ────────────────────────────────────────

    /**
     * Returns pre-computed analytics for a psychometric test from the two materialized views:
     * <ul>
     *   <li>{@code mv_psychometric_test_summary} — result status counts and focus metrics</li>
     *   <li>{@code mv_psychometric_scale_stats} — per-scale sten histogram and score averages</li>
     * </ul>
     *
     * <p>Scale rows where {@code resultCount < MIN_RESPONDENTS} are excluded (privacy threshold).
     * Data freshness depends on the {@code PsychometricAnalyticsRefreshJob} interval (default 10 min).
     */
    @Transactional(readOnly = true)
    public PsychometricTestAnalyticsDto getTestAnalytics(UUID testId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Object[] summary = psychometricMvRepo.findTestSummary(testId);
        List<Object[]> scaleRows = psychometricMvRepo.findScaleStatsByTest(testId, MIN_RESPONDENTS);

        long total     = summary != null ? ((Number) summary[0]).longValue() : 0L;
        long pending   = summary != null ? ((Number) summary[1]).longValue() : 0L;
        long scored    = summary != null ? ((Number) summary[2]).longValue() : 0L;
        long reviewed  = summary != null ? ((Number) summary[3]).longValue() : 0L;
        long flagged   = summary != null ? ((Number) summary[4]).longValue() : 0L;
        LocalDateTime lastScoredAt = (summary != null && summary[5] != null)
                ? ((Timestamp) summary[5]).toLocalDateTime() : null;
        Double avgFocus = (summary != null && summary[6] != null)
                ? ((Number) summary[6]).doubleValue() : null;

        List<PsychometricTestAnalyticsDto.ScaleAnalyticsDto> scales = scaleRows.stream()
                .map(row -> new PsychometricTestAnalyticsDto.ScaleAnalyticsDto(
                        (UUID) row[0],
                        ((Number) row[1]).longValue(),
                        row[2] != null ? ((Number) row[2]).doubleValue() : null,
                        row[3] != null ? ((Number) row[3]).doubleValue() : null,
                        row[4] != null ? ((Number) row[4]).doubleValue() : null,
                        row[5] != null ? ((Number) row[5]).doubleValue() : null,
                        new long[]{
                            ((Number) row[6]).longValue(),  ((Number) row[7]).longValue(),
                            ((Number) row[8]).longValue(),  ((Number) row[9]).longValue(),
                            ((Number) row[10]).longValue(), ((Number) row[11]).longValue(),
                            ((Number) row[12]).longValue(), ((Number) row[13]).longValue(),
                            ((Number) row[14]).longValue(), ((Number) row[15]).longValue()
                        }))
                .toList();

        return new PsychometricTestAnalyticsDto(
                testId, total, pending, scored, reviewed, flagged,
                lastScoredAt, avgFocus, scales);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Validates that {@code timeLimitSecs} is consistent with the capability
     * profile for the given test type.
     *
     * <ul>
     *   <li>COGNITIVE: time limit is required and must be positive.</li>
     *   <li>PERSONALITY: time limit must be absent (null).</li>
     * </ul>
     */
    /**
     * Throws 409 CONFLICT if the test is ACTIVE, preventing scoring-affecting edits.
     * Name, description, and instructions are exempt — they don't affect scoring.
     * To change scoring-affecting fields on an ACTIVE test, use {@code revise()}.
     */
    private void assertEditableScoring(PsychometricTest test) {
        if (test.getStatus() == TestStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Test is ACTIVE — use revise() to change scoring-affecting fields");
        }
    }

    private void validateTimeLimitForType(TestType testType, Integer timeLimitSecs) {
        TestTypeCapabilities caps = TestTypeCapabilities.of(testType);
        if (caps.timeLimitRequired && (timeLimitSecs == null || timeLimitSecs <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    testType.name() + " tests require a positive timeLimitSecs");
        }
        if (!caps.timeLimitVisible && timeLimitSecs != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    testType.name() + " tests must not have a time limit");
        }
    }

    private PsychometricTestDto toTestDto(PsychometricTest t, int scaleCount, int questionCount) {
        PsychometricInstrument inst = t.getInstrument();
        UUID supersedesId = t.getSupersedes() != null ? t.getSupersedes().getId() : null;
        com.edge.pulse.data.dto.psychometric.TestApprovalRequestDto pendingRequest =
                testApprovalRequestRepository
                        .findFirstByTestIdAndStatus(t.getId(), com.edge.pulse.data.enums.TestApprovalStatus.PENDING)
                        .map(testApprovalMapper::toDto)
                        .orElse(null);
        return new PsychometricTestDto(
                t.getId(),
                t.getForm().getId(),
                t.getName(),
                t.getDescription(),
                t.getInstructions(),
                t.getTestType().name(),
                t.getTimeLimitSecs(),
                t.getStatus().name(),
                t.getVersion(),
                t.getCreatedAt(),
                questionCount,
                scaleCount,
                inst == null ? null : inst.getDisplayName(),
                inst == null ? null : inst.getId(),
                supersedesId,
                pendingRequest
        );
    }

    // ── UI-driven Scoring Key ─────────────────────────────────────────────────

    /**
     * Returns the items in the current ACTIVE scoring key for the given test,
     * or an empty list when no active key exists yet.
     */
    @Transactional(readOnly = true)
    public List<ScoringKeyItemDto> getScoringKey(UUID testId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return scoringKeyVersionRepository
                .findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE)
                .map(key -> scoringKeyItemRepository.findByScoringKeyIdWithDetails(key.getId())
                        .stream().map(this::toScoringKeyItemDto).toList())
                .orElse(List.of());
    }

    /**
     * Replaces the active scoring key for a test with the supplied items.
     * Atomically deprecates the current active key and creates a new ACTIVE version.
     * An empty list clears the key.
     */
    @Transactional
    public List<ScoringKeyItemDto> saveScoringKey(UUID testId,
                                                   List<ScoringKeyItemRequest> items,
                                                   UUID userId) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        // Batch-load all referenced entities — eliminates per-item DB round-trips
        Set<UUID> questionIds = items.stream().map(ScoringKeyItemRequest::questionId)
                .collect(Collectors.toSet());
        Set<UUID> scaleIds = items.stream().map(ScoringKeyItemRequest::scaleId)
                .collect(Collectors.toSet());
        Set<UUID> answerIds = items.stream().map(ScoringKeyItemRequest::correctAnswerId)
                .filter(id -> id != null).collect(Collectors.toSet());
        var questionMap = questionRepository.findAllById(questionIds).stream()
                .collect(Collectors.toMap(q -> q.getId(), q -> q));
        Map<UUID, PsychometricScale> scaleMap = scaleRepository.findAllById(scaleIds).stream()
                .collect(Collectors.toMap(PsychometricScale::getId, s -> s));
        var answerMap = candidateAnswerRepository.findAllById(answerIds).stream()
                .collect(Collectors.toMap(a -> a.getId(), a -> a));

        // Validate — all checks use in-memory maps
        for (ScoringKeyItemRequest req : items) {
            if (!questionMap.containsKey(req.questionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Question not found: " + req.questionId());
            }
            PsychometricScale scale = scaleMap.get(req.scaleId());
            if (scale == null || !scale.getTest().getId().equals(testId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Scale not found or does not belong to this test: " + req.scaleId());
            }
        }

        scoringKeyVersionRepository.deprecateActiveKeysByTestId(testId);

        int nextVersion = scoringKeyVersionRepository.findMaxVersionByTestId(testId)
                .map(v -> v + 1).orElse(1);

        ScoringKeyVersion key = scoringKeyVersionRepository.save(
                ScoringKeyVersion.builder()
                        .test(test).version(nextVersion).label("UI v" + nextVersion)
                        .status(ScoringKeyStatus.ACTIVE).publishedBy(user)
                        .publishedAt(LocalDateTime.now()).build());

        List<ScoringKeyItemDto> result = new ArrayList<>();
        for (ScoringKeyItemRequest req : items) {
            var question = questionMap.get(req.questionId());
            var scale = scaleMap.get(req.scaleId());
            var correctAnswer = req.correctAnswerId() != null ? answerMap.get(req.correctAnswerId()) : null;

            ScoreDirection direction = ScoreDirection.FORWARD;
            if (req.direction() != null && !req.direction().isBlank()) {
                try { direction = ScoreDirection.valueOf(req.direction().toUpperCase()); }
                catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Invalid direction: " + req.direction());
                }
            }
            BigDecimal weight = req.weight() != null ? req.weight() : BigDecimal.ONE;

            ScoringKeyItem item = scoringKeyItemRepository.save(
                    ScoringKeyItem.builder()
                            .scoringKey(key).question(question).scale(scale)
                            .direction(direction).weight(weight)
                            .correctAnswer(correctAnswer).partialCredit(req.partialCredit())
                            .itemStrategy(req.itemStrategy())
                            .build());

            result.add(toScoringKeyItemDto(item));
        }

        auditService.logAction(userId, "SCORING_KEY_SAVE", "ScoringKeyVersion", key.getId(),
                auditService.buildDetail("testId", testId, "itemCount", items.size(),
                        "version", nextVersion), null);
        return result;
    }

    // ── UI-driven Norm Table ──────────────────────────────────────────────────

    /**
     * Returns the entries in the current VALIDATED norm table for the given test,
     * or an empty list when no table exists yet.
     */
    @Transactional(readOnly = true)
    public List<NormEntryDto> getNormTable(UUID testId) {
        if (!testRepository.existsById(testId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return normTableVersionRepository
                .findFirstByTestIdAndStatus(testId, NormStatus.VALIDATED)
                .map(table -> normEntryRepository.findByNormTableIdWithScale(table.getId())
                        .stream().map(this::toNormEntryDto).toList())
                .orElse(List.of());
    }

    /**
     * Replaces the active norm table for a test with the supplied entries.
     * Atomically deprecates the current VALIDATED table and creates a new VALIDATED version.
     * An empty list clears the table.
     */
    @Transactional
    public List<NormEntryDto> saveNormTable(UUID testId,
                                             List<NormEntryRequest> entries,
                                             UUID userId) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertEditableScoring(test);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        // Batch-load all referenced scales — eliminates per-entry DB round-trips
        Set<UUID> scaleIds = entries.stream().map(NormEntryRequest::scaleId)
                .collect(Collectors.toSet());
        Map<UUID, PsychometricScale> scaleMap = scaleRepository.findAllById(scaleIds).stream()
                .collect(Collectors.toMap(PsychometricScale::getId, s -> s));

        // Validate — all checks use in-memory map
        for (NormEntryRequest req : entries) {
            PsychometricScale scale = scaleMap.get(req.scaleId());
            if (scale == null || !scale.getTest().getId().equals(testId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Scale not found or does not belong to this test: " + req.scaleId());
            }
        }

        normTableVersionRepository.deprecateValidatedNormsByTestId(testId);

        int nextVersion = normTableVersionRepository.findMaxVersionByTestId(testId)
                .map(v -> v + 1).orElse(1);

        NormTableVersion table = normTableVersionRepository.save(
                NormTableVersion.builder()
                        .test(test).version(nextVersion).label("UI v" + nextVersion)
                        .status(NormStatus.VALIDATED).publishedBy(user)
                        .publishedAt(LocalDateTime.now()).build());

        List<NormEntryDto> result = new ArrayList<>();
        for (NormEntryRequest req : entries) {
            PsychometricScale scale = scaleMap.get(req.scaleId());
            NormEntry entry = normEntryRepository.save(
                    NormEntry.builder()
                            .normTable(table).scale(scale).stenScore(req.stenScore())
                            .rawScoreMin(req.rawScoreMin()).rawScoreMax(req.rawScoreMax())
                            .percentile(req.percentile()).zScore(req.zScore())
                            .build());
            result.add(toNormEntryDto(entry, scale));
        }

        auditService.logAction(userId, "NORM_TABLE_SAVE", "NormTableVersion", table.getId(),
                auditService.buildDetail("testId", testId, "entryCount", entries.size(),
                        "version", nextVersion), null);
        return result;
    }

    // ── Parametric Norm Persistence ───────────────────────────────────────────

    /**
     * Creates a new VALIDATED {@link NormTableVersion} with strategy PARAMETRIC
     * and persists one {@link NormScaleParam} row per supplied request.
     *
     * <p>Atomically deprecates any prior VALIDATED norm table before creating
     * the new one — the same pattern used by {@link #saveNormTable}.
     */
    @Transactional
    public void saveParametricNorms(UUID testId, List<NormScaleParamRequest> params, UUID userId) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Test not found: " + testId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + userId));

        normTableVersionRepository.deprecateValidatedNormsByTestId(testId);

        int nextVersion = normTableVersionRepository.findMaxVersionByTestId(testId)
                .map(v -> v + 1).orElse(1);

        NormTableVersion table = normTableVersionRepository.save(
                NormTableVersion.builder()
                        .test(test)
                        .version(nextVersion)
                        .label("Imported parametric norms v" + nextVersion)
                        .normStrategy(NormStrategyType.PARAMETRIC)
                        .status(NormStatus.VALIDATED)
                        .publishedBy(user)
                        .publishedAt(LocalDateTime.now())
                        .build());

        for (NormScaleParamRequest r : params) {
            PsychometricScale scale = scaleRepository.findById(r.scaleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Scale not found: " + r.scaleId()));
            normScaleParamRepository.save(NormScaleParam.builder()
                    .normTable(table)
                    .scale(scale)
                    .mean(r.mean())
                    .sd(r.sd())
                    .tFactor(r.tFactor())
                    .tOffset(r.tOffset())
                    .tClipLo(r.tClipLo())
                    .tClipHi(r.tClipHi())
                    .sampleSize(r.sampleSize())
                    .build());
        }

        auditService.logAction(userId, "PARAMETRIC_NORM_SAVE", "NormTableVersion", table.getId(),
                auditService.buildDetail("testId", testId, "paramCount", params.size(),
                        "version", nextVersion), null);
    }

    private ScoringKeyItemDto toScoringKeyItemDto(ScoringKeyItem item) {
        var ca = item.getCorrectAnswer();
        return new ScoringKeyItemDto(
                item.getQuestion().getId(),
                item.getQuestion().getBody(),
                item.getQuestion().getQuestionType().name(),
                item.getScale().getId(),
                item.getScale().getName(),
                item.getDirection().name(),
                item.getWeight(),
                ca != null ? ca.getId() : null,
                ca != null ? ca.getLabel() : null,
                item.isPartialCredit());
    }

    private NormEntryDto toNormEntryDto(NormEntry entry) {
        return toNormEntryDto(entry, entry.getScale());
    }

    private NormEntryDto toNormEntryDto(NormEntry entry, PsychometricScale scale) {
        return new NormEntryDto(
                scale.getId(), scale.getName(),
                entry.getStenScore(),
                entry.getRawScoreMin(), entry.getRawScoreMax(),
                entry.getPercentile(), entry.getZScore());
    }

    private int countScales(UUID testId) {
        return scaleRepository.countByTestId(testId);
    }

    private ScoreMethod parseScoreMethod(String value) {
        try {
            return ScoreMethod.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid scoreMethod: '" + value + "'. Valid values: SUM, MEAN");
        }
    }

    private PsychometricScaleDto toScaleDto(PsychometricScale s) {
        return new PsychometricScaleDto(
                s.getId(),
                s.getTest().getId(),
                s.getParentScale() != null ? s.getParentScale().getId() : null,
                s.getName(),
                s.getDescription(),
                s.getScoreMethod().name(),
                s.getDisplayOrder(),
                s.getCompositeMethod(),
                s.getCompositeBasis(),
                s.getCompositeRoundingScale()
        );
    }

    private TestResultSummaryDto toSummary(TestResult r) {
        UUID userId = r.getSession().getUser() != null ? r.getSession().getUser().getId() : null;
        String userName = r.getSession().getUser() != null
                ? r.getSession().getUser().getDisplayName() : null;
        return new TestResultSummaryDto(
                r.getId(),
                r.getTest().getId(),
                r.getSession().getId(),
                userId,
                userName,
                r.getTest().getName(),
                r.getStatus(),
                r.getScoredAt(),
                r.getReviewedAt(),
                r.getReviewNotes(),
                r.getFocusLossCount(),
                r.getScoringKeyVersion() != null ? r.getScoringKeyVersion().getId() : null,
                r.getNormTableVersion() != null ? r.getNormTableVersion().getId() : null
        );
    }
}
