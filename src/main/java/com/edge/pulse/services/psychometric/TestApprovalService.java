package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.CompetencyScaleWeight;
import com.edge.pulse.data.models.psychometric.NormEntry;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ResultVisibilityPolicy;
import com.edge.pulse.data.models.psychometric.ScoringKeyCorrectAnswer;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.ReviewTestApprovalRequest;
import com.edge.pulse.data.dto.psychometric.TestApprovalRequestDto;
import com.edge.pulse.mappers.psychometric.TestApprovalMapper;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScaleWeightRepository;
import com.edge.pulse.repositories.psychometric.NormEntryRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyCorrectAnswerRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository;
import com.edge.pulse.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the dual-control approval lifecycle for psychometric tests:
 * DRAFT → PENDING_APPROVAL → ACTIVE (or back to DRAFT on rejection).
 *
 * <p>Mirrors the {@code RoleChangeService} pattern. Every transition is
 * transactional and writes an {@code audit_logs} row.
 */
@Service
@RequiredArgsConstructor
public class TestApprovalService {

    private final PsychometricTestRepository testRepository;
    private final UserRepository userRepository;
    private final FormRepository formRepository;
    private final QuestionRepository questionRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final TestApprovalRequestRepository approvalRequestRepository;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final ScoringKeyItemRepository scoringKeyItemRepository;
    private final ScoringKeyCorrectAnswerRepository scoringKeyCorrectAnswerRepository;
    private final NormTableVersionRepository normTableVersionRepository;
    private final NormEntryRepository normEntryRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final CompetencyScaleWeightRepository competencyScaleWeightRepository;
    private final ResultVisibilityPolicyRepository resultVisibilityPolicyRepository;
    private final TestApprovalMapper approvalMapper;
    private final AuditService auditService;
    private final PsychometricAdminService psychometricAdminService;

    // ── submit ────────────────────────────────────────────────────────────────

    /**
     * Submits a DRAFT test for approval: DRAFT → PENDING_APPROVAL.
     *
     * @throws ResponseStatusException 409 if the test is not DRAFT or already has a PENDING request
     * @throws ResponseStatusException 422 if the test is not scoreable
     */
    @Transactional
    public TestApprovalRequest submit(UUID testId, UUID submitterId) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found"));

        if (test.getStatus() != TestStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only DRAFT tests can be submitted; current status: " + test.getStatus());
        }
        if (approvalRequestRepository.existsByTestIdAndStatus(testId, TestApprovalStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending approval request already exists for this test");
        }

        List<String> gaps = scoreabilityGaps(test);
        if (!gaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Test is not scoreable: " + String.join(", ", gaps));
        }

        User submitter = userRepository.findById(submitterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));

        test.setStatus(TestStatus.PENDING_APPROVAL);
        testRepository.save(test);

        TestApprovalRequest request = TestApprovalRequest.builder()
                .test(test)
                .testVersion(test.getVersion())
                .submittedBy(submitter)
                .status(TestApprovalStatus.PENDING)
                .build();

        // M1: the partial-unique index idx_test_approval_open prevents duplicate PENDING rows.
        // The existsByTestIdAndStatus check above is the primary guard; this catches the rare
        // concurrent duplicate and surfaces it as a 409 rather than a 500.
        try {
            approvalRequestRepository.save(request);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A pending approval request already exists for this test");
        }

        auditService.logAction(submitterId, "PSYCH_TEST_SUBMITTED", "PsychometricTest", testId,
                auditService.buildDetail("version", test.getVersion(), "requestId", request.getId()), null);

        return request;
    }

    // ── review ────────────────────────────────────────────────────────────────

    /**
     * Reviews a PENDING approval request: APPROVE → ACTIVE (+ retire superseded version);
     * REJECT → DRAFT (comment required).
     *
     * @throws ResponseStatusException 403 if reviewer == submitter (segregation of duties)
     * @throws ResponseStatusException 409 if the request is not PENDING
     */
    @Transactional
    public TestApprovalRequest review(UUID reviewerId, UUID requestId,
                                       String decision, String approvalReference, String comment) {
        TestApprovalRequest request = approvalRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Approval request not found"));

        if (request.getStatus() != TestApprovalStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Request is not PENDING; current status: " + request.getStatus());
        }

        if (request.getSubmittedBy().getId().equals(reviewerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot approve your own submission (segregation of duties)");
        }

        // Validate REJECT comment before any DB round-trips so validation error is surfaced early
        if ("REJECT".equalsIgnoreCase(decision) && (comment == null || comment.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A comment (reason for rejection) is required");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reviewer not found"));

        request.setReviewedBy(reviewer);
        request.setReviewedAt(LocalDateTime.now());

        PsychometricTest test = request.getTest();

        if ("APPROVE".equalsIgnoreCase(decision)) {
            request.setStatus(TestApprovalStatus.APPROVED);
            request.setApprovalReference(approvalReference);
            request.setReviewComment(comment);

            test.setStatus(TestStatus.ACTIVE);

            // M3: if a supersedes FK is present, load the prior from DB; 404 if genuinely missing.
            if (test.getSupersedes() != null) {
                PsychometricTest prior = testRepository.findById(test.getSupersedes().getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Superseded prior test not found: " + test.getSupersedes().getId()));
                prior.setStatus(TestStatus.RETIRED);
                testRepository.save(prior);
            }

            testRepository.save(test);
            approvalRequestRepository.save(request);

            auditService.logAction(reviewerId, "PSYCH_TEST_APPROVED", "PsychometricTest", test.getId(),
                    auditService.buildDetail("approvalReference", approvalReference,
                            "requestId", requestId, "version", test.getVersion()), null);

        } else if ("REJECT".equalsIgnoreCase(decision)) {
            request.setStatus(TestApprovalStatus.REJECTED);
            request.setReviewComment(comment);

            test.setStatus(TestStatus.DRAFT);
            testRepository.save(test);
            approvalRequestRepository.save(request);

            auditService.logAction(reviewerId, "PSYCH_TEST_REJECTED", "PsychometricTest", test.getId(),
                    auditService.buildDetail("reason", comment, "requestId", requestId), null);

        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Decision must be APPROVE or REJECT");
        }

        return request;
    }

    // ── revise ────────────────────────────────────────────────────────────────

    /**
     * Creates a new DRAFT version of an ACTIVE test (version + 1, supersedes = prior),
     * performing a deep copy of all scoring content from the prior version.
     *
     * <p>Deep-copy inventory:
     * <ul>
     *   <li>Questions (with CandidateAnswers) from prior.form → new form</li>
     *   <li>PsychometricScale rows (with parent re-mapping) — test_id = clone</li>
     *   <li>ACTIVE ScoringKeyVersion + ScoringKeyItems (question/scale remapped)</li>
     *   <li>VALIDATED NormTableVersion + NormEntries (scale remapped)</li>
     *   <li>CompetencyScaleWeights (scale remapped)</li>
     *   <li>ResultVisibilityPolicy rows</li>
     * </ul>
     *
     * <p>The prior ACTIVE test remains ACTIVE until the new version is approved.
     *
     * @throws ResponseStatusException 409 if the test is not ACTIVE
     */
    @Transactional
    public PsychometricTest revise(UUID testId, UUID userId) {
        PsychometricTest prior = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Test not found"));

        if (prior.getStatus() != TestStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only ACTIVE tests can be revised; current status: " + prior.getStatus());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "User not found"));

        // ── Step 1: create new Form ───────────────────────────────────────────
        Form newForm = formRepository.save(Form.builder()
                .title(prior.getName())
                .description(prior.getDescription())
                .formType(prior.getForm().getFormType())
                .anonWindowMinutes(0)
                .build());

        // ── Step 2: create clone test shell ──────────────────────────────────
        PsychometricTest clone = testRepository.save(PsychometricTest.builder()
                .form(newForm)
                .name(prior.getName())
                .description(prior.getDescription())
                .instructions(prior.getInstructions())
                .testType(prior.getTestType())
                .timeLimitSecs(prior.getTimeLimitSecs())
                .instrument(prior.getInstrument())
                .createdBy(user)
                .status(TestStatus.DRAFT)
                .version(prior.getVersion() + 1)
                .supersedes(prior)
                .build());

        // ── Step 3: deep-copy questions (build old→new question id map) ───────
        Map<UUID, UUID> questionIdMap = deepCopyQuestions(prior, newForm);

        // ── Step 4: deep-copy scales (build old→new scale id map) ────────────
        Map<UUID, UUID> scaleIdMap = deepCopyScales(prior, clone);

        // ── Step 5: deep-copy ACTIVE scoring key ─────────────────────────────
        deepCopyScoringKey(prior, clone, questionIdMap, scaleIdMap);

        // ── Step 6: deep-copy VALIDATED norm table ────────────────────────────
        deepCopyNormTable(prior, clone, scaleIdMap);

        // ── Step 7: deep-copy competency-scale weights ───────────────────────
        deepCopyCompetencyWeights(prior, clone, scaleIdMap);

        // ── Step 8: deep-copy result visibility policies ──────────────────────
        deepCopyVisibilityPolicies(prior, clone);

        auditService.logAction(userId, "PSYCH_TEST_REVISED", "PsychometricTest", prior.getId(),
                auditService.buildDetail("priorId", prior.getId(), "newVersion", clone.getVersion(),
                        "cloneId", clone.getId()), null);

        return clone;
    }

    // ── deep-copy helpers ─────────────────────────────────────────────────────

    /**
     * Copies all questions from the prior test's form to the new form.
     * Copies each question's CandidateAnswers with updated question references.
     *
     * @return map of oldQuestionId → newQuestionId
     */
    private Map<UUID, UUID> deepCopyQuestions(PsychometricTest prior, Form newForm) {
        List<Question> priorQuestions = questionRepository.findByFormIdOrderByDisplayOrderAsc(
                prior.getForm().getId());
        Map<UUID, UUID> questionIdMap = new HashMap<>();

        for (Question pq : priorQuestions) {
            Question newQuestion = questionRepository.save(Question.builder()
                    .form(newForm)
                    .body(pq.getBody())
                    .bodyAr(pq.getBodyAr())
                    .questionType(pq.getQuestionType())
                    .displayOrder(pq.getDisplayOrder())
                    .scaleMin(pq.getScaleMin())
                    .scaleMax(pq.getScaleMax())
                    .minLabel(pq.getMinLabel())
                    .maxLabel(pq.getMaxLabel())
                    .minLabelAr(pq.getMinLabelAr())
                    .maxLabelAr(pq.getMaxLabelAr())
                    .subjectLabels(pq.getSubjectLabels() != null ? new ArrayList<>(pq.getSubjectLabels()) : null)
                    .subjectLabelsAr(pq.getSubjectLabelsAr() != null ? new ArrayList<>(pq.getSubjectLabelsAr()) : null)
                    .forcedChoicePairs(pq.getForcedChoicePairs() != null ? new ArrayList<>(pq.getForcedChoicePairs()) : null)
                    .effectiveDate(pq.getEffectiveDate())
                    .expirationDate(pq.getExpirationDate())
                    .build());

            questionIdMap.put(pq.getId(), newQuestion.getId());

            // Copy candidate answers
            List<CandidateAnswer> priorAnswers = candidateAnswerRepository
                    .findByQuestionIdOrderByDisplayOrderAsc(pq.getId());
            for (CandidateAnswer pa : priorAnswers) {
                candidateAnswerRepository.save(CandidateAnswer.builder()
                        .question(newQuestion)
                        .label(pa.getLabel())
                        .labelAr(pa.getLabelAr())
                        .displayOrder(pa.getDisplayOrder())
                        .isCorrect(pa.getIsCorrect())
                        .build());
                // Note: tagScale references old scales — these will be updated after scales are copied.
                // tagScale on CandidateAnswer is display-only and is omitted from the deep copy
                // to avoid a circular dependency; the scoring key's scale references are the source of truth.
            }
        }
        return questionIdMap;
    }

    /**
     * Copies all scales from the prior test to the clone.
     * Parent references are resolved using old→new id mapping in two passes:
     * first roots (no parent), then children.
     *
     * @return map of oldScaleId → newScaleId
     */
    private Map<UUID, UUID> deepCopyScales(PsychometricTest prior, PsychometricTest clone) {
        List<PsychometricScale> priorScales = scaleRepository.findByTestIdOrdered(prior.getId());
        Map<UUID, UUID> scaleIdMap = new HashMap<>();

        // Pass 1: copy root scales (parentScale == null)
        for (PsychometricScale ps : priorScales) {
            if (ps.getParentScale() == null) {
                PsychometricScale newScale = scaleRepository.save(PsychometricScale.builder()
                        .test(clone)
                        .parentScale(null)
                        .name(ps.getName())
                        .description(ps.getDescription())
                        .scoreMethod(ps.getScoreMethod())
                        .displayOrder(ps.getDisplayOrder())
                        .compositeMethod(ps.getCompositeMethod())
                        .compositeBasis(ps.getCompositeBasis())
                        .compositeRoundingScale(ps.getCompositeRoundingScale())
                        .resultMode(ps.getResultMode())
                        .restricted(ps.isRestricted())
                        .build());
                scaleIdMap.put(ps.getId(), newScale.getId());
            }
        }

        // Pass 2: copy child scales (parentScale != null), resolving parent via map
        for (PsychometricScale ps : priorScales) {
            if (ps.getParentScale() != null) {
                UUID newParentId = scaleIdMap.get(ps.getParentScale().getId());
                PsychometricScale newParent = newParentId != null
                        ? scaleRepository.findById(newParentId).orElse(null)
                        : null;
                PsychometricScale newScale = scaleRepository.save(PsychometricScale.builder()
                        .test(clone)
                        .parentScale(newParent)
                        .name(ps.getName())
                        .description(ps.getDescription())
                        .scoreMethod(ps.getScoreMethod())
                        .displayOrder(ps.getDisplayOrder())
                        .compositeMethod(ps.getCompositeMethod())
                        .compositeBasis(ps.getCompositeBasis())
                        .compositeRoundingScale(ps.getCompositeRoundingScale())
                        .resultMode(ps.getResultMode())
                        .restricted(ps.isRestricted())
                        .build());
                scaleIdMap.put(ps.getId(), newScale.getId());
            }
        }

        return scaleIdMap;
    }

    /**
     * Copies the prior test's ACTIVE scoring key version (if present) to the clone,
     * re-mapping question and scale references via the provided id maps.
     */
    private void deepCopyScoringKey(PsychometricTest prior, PsychometricTest clone,
                                     Map<UUID, UUID> questionIdMap, Map<UUID, UUID> scaleIdMap) {
        scoringKeyVersionRepository
                .findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE)
                .ifPresent(priorKey -> {
                    ScoringKeyVersion newKey = scoringKeyVersionRepository.save(ScoringKeyVersion.builder()
                            .test(clone)
                            .version(priorKey.getVersion())
                            .label(priorKey.getLabel())
                            .status(ScoringKeyStatus.ACTIVE)
                            .effectiveFrom(priorKey.getEffectiveFrom())
                            .effectiveUntil(priorKey.getEffectiveUntil())
                            .build());

                    List<ScoringKeyItem> priorItems =
                            scoringKeyItemRepository.findByScoringKeyIdWithDetails(priorKey.getId());

                    for (ScoringKeyItem pi : priorItems) {
                        UUID newQuestionId = questionIdMap.get(pi.getQuestion().getId());
                        UUID newScaleId = scaleIdMap.get(pi.getScale().getId());

                        if (newQuestionId == null || newScaleId == null) {
                            // Defensive: skip items whose question/scale wasn't copied (shouldn't happen)
                            continue;
                        }

                        Question newQuestion = questionRepository.findById(newQuestionId).orElse(null);
                        PsychometricScale newScale = scaleRepository.findById(newScaleId).orElse(null);

                        if (newQuestion == null || newScale == null) {
                            continue;
                        }

                        // Resolve new correctAnswer: find the answer with same displayOrder on new question
                        CandidateAnswer newCorrectAnswer = null;
                        if (pi.getCorrectAnswer() != null) {
                            List<CandidateAnswer> newAnswers =
                                    candidateAnswerRepository.findByQuestionIdOrderByDisplayOrderAsc(newQuestionId);
                            final int targetOrder = pi.getCorrectAnswer().getDisplayOrder();
                            newCorrectAnswer = newAnswers.stream()
                                    .filter(a -> a.getDisplayOrder() == targetOrder)
                                    .findFirst()
                                    .orElse(null);
                        }

                        ScoringKeyItem newItem = scoringKeyItemRepository.save(ScoringKeyItem.builder()
                                .scoringKey(newKey)
                                .scale(newScale)
                                .question(newQuestion)
                                .direction(pi.getDirection())
                                .weight(pi.getWeight())
                                .correctAnswer(newCorrectAnswer)
                                .itemStrategy(pi.getItemStrategy())
                                .partialCredit(pi.isPartialCredit())
                                .build());

                        // Copy CHOICE_MULTIPLE correct-answer sets (ScoringKeyCorrectAnswer junction rows)
                        List<ScoringKeyCorrectAnswer> priorCorrectAnswers =
                                scoringKeyCorrectAnswerRepository.findByItemIdIn(List.of(pi.getId()));
                        for (ScoringKeyCorrectAnswer pca : priorCorrectAnswers) {
                            // Find matching new CandidateAnswer by displayOrder on new question
                            List<CandidateAnswer> newAnswers =
                                    candidateAnswerRepository.findByQuestionIdOrderByDisplayOrderAsc(newQuestionId);
                            final int caOrder = pca.getCandidateAnswer().getDisplayOrder();
                            CandidateAnswer newCa = newAnswers.stream()
                                    .filter(a -> a.getDisplayOrder() == caOrder)
                                    .findFirst()
                                    .orElse(null);
                            if (newCa != null) {
                                scoringKeyCorrectAnswerRepository.save(ScoringKeyCorrectAnswer.builder()
                                        .scoringKeyItem(newItem)
                                        .candidateAnswer(newCa)
                                        .build());
                            }
                        }
                    }
                });
    }

    /**
     * Copies the prior test's VALIDATED norm table version (if present) to the clone,
     * re-mapping scale references via the provided id map.
     */
    private void deepCopyNormTable(PsychometricTest prior, PsychometricTest clone,
                                    Map<UUID, UUID> scaleIdMap) {
        normTableVersionRepository
                .findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED)
                .ifPresent(priorNorm -> {
                    NormTableVersion newNorm = normTableVersionRepository.save(NormTableVersion.builder()
                            .test(clone)
                            .version(priorNorm.getVersion())
                            .label(priorNorm.getLabel())
                            .sampleSize(priorNorm.getSampleSize())
                            .status(NormStatus.VALIDATED)
                            .normStrategy(priorNorm.getNormStrategy())
                            .effectiveFrom(priorNorm.getEffectiveFrom())
                            .effectiveUntil(priorNorm.getEffectiveUntil())
                            .build());

                    List<NormEntry> priorEntries = normEntryRepository.findByNormTableId(priorNorm.getId());
                    for (NormEntry pe : priorEntries) {
                        UUID newScaleId = scaleIdMap.get(pe.getScale().getId());
                        if (newScaleId == null) {
                            continue;
                        }
                        PsychometricScale newScale = scaleRepository.findById(newScaleId).orElse(null);
                        if (newScale == null) {
                            continue;
                        }
                        normEntryRepository.save(NormEntry.builder()
                                .normTable(newNorm)
                                .scale(newScale)
                                .rawScoreMin(pe.getRawScoreMin())
                                .rawScoreMax(pe.getRawScoreMax())
                                .percentile(pe.getPercentile())
                                .stenScore(pe.getStenScore())
                                .zScore(pe.getZScore())
                                .build());
                    }
                });
    }

    /**
     * Copies competency-scale weights from prior scales to corresponding new scales.
     */
    private void deepCopyCompetencyWeights(PsychometricTest prior, PsychometricTest clone,
                                            Map<UUID, UUID> scaleIdMap) {
        List<UUID> priorScaleIds = scaleRepository.findByTestId(prior.getId())
                .stream().map(PsychometricScale::getId).toList();

        if (priorScaleIds.isEmpty()) {
            return;
        }

        List<CompetencyScaleWeight> priorWeights =
                competencyScaleWeightRepository.findByScaleIdIn(priorScaleIds);
        for (CompetencyScaleWeight pw : priorWeights) {
            UUID newScaleId = scaleIdMap.get(pw.getScale().getId());
            if (newScaleId == null) {
                continue;
            }
            PsychometricScale newScale = scaleRepository.findById(newScaleId).orElse(null);
            if (newScale == null) {
                continue;
            }
            competencyScaleWeightRepository.save(CompetencyScaleWeight.builder()
                    .competency(pw.getCompetency())
                    .scale(newScale)
                    .weight(pw.getWeight())
                    .direction(pw.getDirection())
                    .build());
        }
    }

    /**
     * Copies result visibility policy rows from the prior test to the clone.
     */
    private void deepCopyVisibilityPolicies(PsychometricTest prior, PsychometricTest clone) {
        List<ResultVisibilityPolicy> priorPolicies =
                resultVisibilityPolicyRepository.findByTestId(prior.getId());
        for (ResultVisibilityPolicy pp : priorPolicies) {
            resultVisibilityPolicyRepository.save(ResultVisibilityPolicy.builder()
                    .test(clone)
                    .audience(pp.getAudience())
                    .showRawScore(pp.isShowRawScore())
                    .showStenProfile(pp.isShowStenProfile())
                    .showPercentile(pp.isShowPercentile())
                    .showCompetencyMap(pp.isShowCompetencyMap())
                    .showPassFailOnly(pp.isShowPassFailOnly())
                    .showScaleBreakdown(pp.isShowScaleBreakdown())
                    .build());
        }
    }

    // ── DTO-returning wrappers (controller convenience) ──────────────────────

    /**
     * Submits a test for approval and returns the updated test DTO.
     * Called by the controller to avoid an extra service call.
     *
     * <p>I2: @Transactional ensures the mutation and the DTO read share one transaction,
     * preventing a split-commit / LazyInitializationException.
     */
    @Transactional
    public PsychometricTestDto submitAndGetDto(UUID testId, UUID submitterId) {
        submit(testId, submitterId);
        return psychometricAdminService.getTest(testId);
    }

    /**
     * Reviews an approval request and returns the approval request DTO.
     *
     * <p>I2: @Transactional ensures the mutation and the DTO read share one transaction.
     */
    @Transactional
    public TestApprovalRequestDto reviewAndGetDto(UUID reviewerId, UUID requestId,
                                                   ReviewTestApprovalRequest body) {
        TestApprovalRequest request = review(reviewerId, requestId,
                body.decision(), body.approvalReference(), body.comment());
        return approvalMapper.toDto(request);
    }

    /**
     * Revises a test and returns the new clone's test DTO.
     *
     * <p>I2: @Transactional ensures the mutation and the DTO read share one transaction.
     */
    @Transactional
    public PsychometricTestDto reviseAndGetDto(UUID testId, UUID userId) {
        PsychometricTest clone = revise(testId, userId);
        return psychometricAdminService.getTest(clone.getId());
    }

    // ── listByStatus ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestApprovalRequest> listByStatus(TestApprovalStatus status) {
        return approvalRequestRepository.findByStatusWithDetails(status);
    }

    /**
     * Lists approval requests as DTOs (used by the controller directly).
     */
    @Transactional(readOnly = true)
    public List<TestApprovalRequestDto> listDtos(TestApprovalStatus status) {
        return listByStatus(status).stream()
                .map(approvalMapper::toDto)
                .toList();
    }

    // ── scoreability gate (private) ──────────────────────────────────────────

    /**
     * Returns a list of missing pieces that prevent submission.
     * Empty list = scoreable.
     *
     * <ul>
     *   <li>COGNITIVE / PERSONALITY (keyed/likert): requires an ACTIVE scoring key AND a
     *       VALIDATED norm table. Both are required to produce normed results.</li>
     *   <li>COMPETENCY: requires at least one competency scale weight on any of the test's scales.</li>
     * </ul>
     */
    private List<String> scoreabilityGaps(PsychometricTest test) {
        List<String> gaps = new ArrayList<>();

        if (test.getTestType() == TestType.COMPETENCY) {
            // COMPETENCY tests are scored via scale weights mapped to global competencies.
            List<UUID> scaleIds = scaleRepository.findByTestId(test.getId())
                    .stream().map(PsychometricScale::getId).toList();
            if (scaleIds.isEmpty() || competencyScaleWeightRepository.findByScaleIdIn(scaleIds).isEmpty()) {
                gaps.add("competency scale weights not configured");
            }
        } else {
            // COGNITIVE / PERSONALITY (keyed/likert): requires an ACTIVE scoring key.
            if (scoringKeyVersionRepository
                    .findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE)
                    .isEmpty()) {
                gaps.add("no active scoring key");
            }
            // I1: also requires a VALIDATED norm table for normed results.
            if (normTableVersionRepository
                    .findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED)
                    .isEmpty()) {
                gaps.add("no validated norm table");
            }
        }
        return gaps;
    }
}
