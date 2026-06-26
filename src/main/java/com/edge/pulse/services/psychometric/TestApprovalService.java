package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScaleWeightRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository;
import com.edge.pulse.services.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final TestApprovalRequestRepository approvalRequestRepository;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final NormTableVersionRepository normTableVersionRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final CompetencyScaleWeightRepository competencyScaleWeightRepository;
    private final AuditService auditService;

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
        approvalRequestRepository.save(request);

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

            // Atomically retire the superseded prior version in the same transaction
            if (test.getSupersedes() != null) {
                PsychometricTest prior = testRepository.findById(test.getSupersedes().getId())
                        .orElse(test.getSupersedes());
                prior.setStatus(TestStatus.RETIRED);
                testRepository.save(prior);
            }

            testRepository.save(test);
            approvalRequestRepository.save(request);

            auditService.logAction(reviewerId, "PSYCH_TEST_APPROVED", "PsychometricTest", test.getId(),
                    auditService.buildDetail("approvalReference", approvalReference,
                            "requestId", requestId, "version", test.getVersion()), null);

        } else if ("REJECT".equalsIgnoreCase(decision)) {
            if (comment == null || comment.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A comment (reason for rejection) is required");
            }
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
     * Creates a new DRAFT version of an ACTIVE test (version + 1, supersedes = prior).
     * The new version is a shallow clone of the test metadata; scoring artifacts are
     * preserved via their existing association with the original test's form/scales.
     * The prior ACTIVE test remains ACTIVE until the new version is approved.
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

        // Clone the test metadata into a new DRAFT version.
        // A new Form is required (psychometric_test has a unique FK on form_id).
        Form newForm = Form.builder()
                .title(prior.getName())
                .description(prior.getDescription())
                .formType(prior.getForm().getFormType())
                .anonWindowMinutes(0)
                .build();

        PsychometricTest clone = PsychometricTest.builder()
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
                .build();

        testRepository.save(clone);

        auditService.logAction(userId, "PSYCH_TEST_REVISED", "PsychometricTest", prior.getId(),
                auditService.buildDetail("priorId", prior.getId(), "newVersion", clone.getVersion(),
                        "cloneId", clone.getId()), null);

        return clone;
    }

    // ── listByStatus ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TestApprovalRequest> listByStatus(TestApprovalStatus status) {
        return approvalRequestRepository.findByStatusWithDetails(status);
    }

    // ── scoreability gate (private) ──────────────────────────────────────────

    /**
     * Returns a list of missing pieces that prevent submission.
     * Empty list = scoreable.
     *
     * <ul>
     *   <li>COGNITIVE / PERSONALITY (keyed/likert): requires an ACTIVE scoring key.</li>
     *   <li>COMPETENCY: requires at least one competency scale weight on any of the test's scales.</li>
     * </ul>
     */
    private List<String> scoreabilityGaps(PsychometricTest test) {
        List<String> gaps = new ArrayList<>();

        if (test.getTestType() == TestType.COMPETENCY) {
            // COMPETENCY tests are scored via scale weights mapped to global competencies.
            List<UUID> scaleIds = scaleRepository.findByTestId(test.getId())
                    .stream().map(s -> s.getId()).toList();
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
        }
        return gaps;
    }
}
