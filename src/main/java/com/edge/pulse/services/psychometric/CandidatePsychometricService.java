package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CandidateTestDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.dto.psychometric.CompetencyScoreDto;
import com.edge.pulse.data.dto.psychometric.ScaleScoreDto;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.data.enums.ResultAudience;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ResultVisibilityPolicy;
import com.edge.pulse.data.models.psychometric.ScaleScore;
import com.edge.pulse.data.models.psychometric.TestResult;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScoreRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScaleScoreRepository;
import com.edge.pulse.repositories.psychometric.TestResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidatePsychometricService {

    private final PsychometricTestRepository testRepository;
    private final TestResultRepository resultRepository;
    private final ScaleScoreRepository scaleScoreRepository;
    private final CompetencyScoreRepository competencyScoreRepository;
    private final ResultVisibilityPolicyRepository policyRepository;
    private final FormAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;

    /** Pre-test screen: metadata visible before the candidate starts. */
    @Transactional(readOnly = true)
    public CandidateTestDto getTestDetails(UUID testId, UUID userId) {
        PsychometricTest test = testRepository.findById(testId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";
        if (!assignmentRepository.hasVisibleAssignment(test.getForm().getId(), userId, orgPath)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        int questionCount = questionRepository
                .countActiveByFormIds(List.of(test.getForm().getId()))
                .stream()
                .findFirst()
                .map(row -> ((Long) row[1]).intValue())
                .orElse(0);
        return new CandidateTestDto(
                test.getId(),
                test.getForm().getId(),
                test.getName(),
                test.getDescription(),
                test.getInstructions(),
                test.getTestType().name(),
                test.getTimeLimitSecs(),
                questionCount
        );
    }

    private static final int MY_RESULTS_MAX = 50;
    private static final int TEAM_RESULTS_MAX = 200;

    /** Result list for the requesting candidate (capped at {@value MY_RESULTS_MAX} most-recent). */
    @Transactional(readOnly = true)
    public List<CandidateTestResultDto> getMyResults(UUID userId) {
        return resultRepository.findByUserId(userId, PageRequest.of(0, MY_RESULTS_MAX)).stream()
                .map(this::toResultSummary)
                .toList();
    }

    /** Result detail with ownership check and visibility-policy masking. */
    @Transactional(readOnly = true)
    public CandidateTestResultDetailsDto getResultDetail(UUID resultId, UUID userId) {
        TestResult result = resultRepository.findByIdWithSessionAndTest(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!result.getSession().getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        ResultVisibilityPolicy policy =
                policyRepository.findByTestIdAndAudience(result.getTest().getId(), ResultAudience.CANDIDATE)
                        .orElse(null);
        List<ScaleScore> scores = scaleScoreRepository.findByResultIdWithScale(result.getId());
        return toResultDetails(result, policy, scores);
    }

    /**
     * Manager view: list of all team results scoped to the manager's org-unit subtree.
     * Capped at {@value TEAM_RESULTS_MAX} most-recent results.
     */
    @Transactional(readOnly = true)
    public List<TestResultSummaryDto> getTeamResults(UUID managerId) {
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        OrganizationalUnit managerOrgUnit = manager.getOrgUnit();
        String managerPath = managerOrgUnit != null ? managerOrgUnit.getPath() : "";
        if (managerPath.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager has no org-unit assigned");
        }
        return resultRepository.findByOrgPathPrefix(managerPath, PageRequest.of(0, TEAM_RESULTS_MAX))
                .stream()
                .map(this::toTeamResultSummary)
                .toList();
    }

    /**
     * Manager view of a team member's result.
     *
     * <p>Validates that the subject's org-unit path falls under the manager's org-unit
     * (path starts with managerPath + "/"), then applies the MANAGER visibility policy.
     */
    @Transactional(readOnly = true)
    public CandidateTestResultDetailsDto getTeamResultDetail(UUID resultId, UUID managerId) {
        TestResult result = resultRepository.findByIdWithSessionAndTest(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));

        OrganizationalUnit managerOrgUnit = manager.getOrgUnit();
        String managerPath = managerOrgUnit != null ? managerOrgUnit.getPath() : "";

        OrganizationalUnit reportOrgUnit = result.getSession().getUser().getOrgUnit();
        String reportPath = reportOrgUnit != null ? reportOrgUnit.getPath() : "";

        boolean inScope = reportPath.startsWith(managerPath + "/") || reportPath.equals(managerPath);
        if (managerPath.isEmpty() || !inScope) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        ResultVisibilityPolicy policy =
                policyRepository.findByTestIdAndAudience(result.getTest().getId(), ResultAudience.MANAGER)
                        .orElse(null);
        List<ScaleScore> scores = scaleScoreRepository.findByResultIdWithScale(result.getId());
        return toResultDetails(result, policy, scores);
    }

    // ── Package-accessible helper (used by PsychometricAdminService) ──────────

    CandidateTestResultDetailsDto toResultDetailsForAudience(UUID resultId, ResultAudience audience) {
        TestResult result = resultRepository.findByIdWithSessionAndTest(resultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ResultVisibilityPolicy policy =
                policyRepository.findByTestIdAndAudience(result.getTest().getId(), audience)
                        .orElse(null);
        List<ScaleScore> scores = scaleScoreRepository.findByResultIdWithScale(result.getId());
        return toResultDetails(result, policy, scores);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CandidateTestResultDto toResultSummary(TestResult r) {
        return new CandidateTestResultDto(
                r.getId(),
                r.getTest().getId(),
                r.getTest().getName(),
                r.getTest().getTestType().name(),
                r.getStatus(),
                r.getSession().getCompletedAt(),
                r.getScoredAt(),
                r.getFocusLossCount()
        );
    }

    private CandidateTestResultDetailsDto toResultDetails(
            TestResult r, ResultVisibilityPolicy policy, List<ScaleScore> scores) {
        boolean showRaw        = policy != null && policy.isShowRawScore();
        boolean showSten       = policy != null && policy.isShowStenProfile();
        boolean showPct        = policy != null && policy.isShowPercentile();
        boolean showBreakdown  = policy != null && policy.isShowScaleBreakdown();
        boolean showCompMap    = policy != null && policy.isShowCompetencyMap();

        List<ScaleScoreDto> scaleDtos = showBreakdown
                ? scores.stream().map(ss -> toScaleScoreDto(ss, showRaw, showSten, showPct)).toList()
                : List.of();

        List<CompetencyScoreDto> competencyDtos = showCompMap
                ? competencyScoreRepository.findByResultIdWithCompetency(r.getId()).stream()
                        .map(cs -> new CompetencyScoreDto(
                                cs.getCompetency().getId(),
                                cs.getCompetency().getName(),
                                cs.getScore()))
                        .toList()
                : List.of();

        return new CandidateTestResultDetailsDto(
                r.getId(),
                r.getTest().getId(),
                r.getTest().getName(),
                r.getTest().getTestType().name(),
                r.getStatus(),
                r.getSession().getCompletedAt(),
                r.getScoredAt(),
                r.getFocusLossCount(),
                showRaw,
                showSten,
                showPct,
                showBreakdown,
                scaleDtos,
                showCompMap,
                competencyDtos
        );
    }

    private ScaleScoreDto toScaleScoreDto(ScaleScore ss, boolean showRaw, boolean showSten, boolean showPct) {
        return new ScaleScoreDto(
                ss.getScale().getId(),
                ss.getScale().getName(),
                showRaw  ? ss.getRawScore()   : null,
                showSten ? ss.getStenScore()  : null,
                showSten ? ss.getTScore()     : null,
                showPct  ? ss.getPercentile() : null,
                showSten ? ss.getZScore()     : null,
                ss.getItemsAnswered(),
                ss.getItemsTotal()
        );
    }

    private TestResultSummaryDto toTeamResultSummary(TestResult r) {
        UUID userId = r.getSession().getUser() != null ? r.getSession().getUser().getId() : null;
        String userName = r.getSession().getUser() != null
                ? r.getSession().getUser().getDisplayName() : null;
        // scoringKeyVersionId / normTableVersionId are HR-admin metadata not needed by managers.
        // Omitting them avoids N+1 lazy loads (those associations are not JOIN FETCHed by
        // findByOrgPathPrefix, and up to TEAM_RESULTS_MAX=200 results would trigger 400 extra queries).
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
                null, // scoringKeyVersionId — not needed by manager view; omitted to avoid N+1
                null  // normTableVersionId  — not needed by manager view; omitted to avoid N+1
        );
    }
}
