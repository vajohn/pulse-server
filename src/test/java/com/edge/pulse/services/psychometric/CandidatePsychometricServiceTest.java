package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.CandidateTestDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.CandidateTestResultDto;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.data.enums.ResultAudience;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.Competency;
import com.edge.pulse.data.models.psychometric.CompetencyScore;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.util.ArrayList;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidatePsychometricServiceTest {

    @Mock PsychometricTestRepository testRepository;
    @Mock TestResultRepository resultRepository;
    @Mock ScaleScoreRepository scaleScoreRepository;
    @Mock CompetencyScoreRepository competencyScoreRepository;
    @Mock ResultVisibilityPolicyRepository policyRepository;
    @Mock FormAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock QuestionRepository questionRepository;

    @InjectMocks CandidatePsychometricService service;

    private UUID userId, testId, surveyId, resultId;
    private Form survey;
    private PsychometricTest psychTest;
    private User candidateUser;
    private ResponseSession session;
    private TestResult testResult;

    /** Org path used when the candidate has an org unit. */
    private static final String ORG_PATH = "/hq/engineering";

    @BeforeEach
    void setUp() {
        userId   = UUID.randomUUID();
        testId   = UUID.randomUUID();
        surveyId = UUID.randomUUID();
        resultId = UUID.randomUUID();

        survey = Form.builder().id(surveyId).title("Big-Five Inventory").questions(List.of()).build();

        psychTest = PsychometricTest.builder()
                .id(testId)
                .form(survey)
                .name("Big-Five Inventory")
                .description("Personality assessment")
                .instructions("Answer honestly.")
                .testType(TestType.PERSONALITY)
                .timeLimitSecs(null)
                .build();

        OrganizationalUnit orgUnit = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path(ORG_PATH).build();
        candidateUser = User.builder().id(userId).orgUnit(orgUnit).build();

        session = ResponseSession.builder()
                .id(UUID.randomUUID())
                .form(survey)
                .user(candidateUser)
                .completedAt(LocalDateTime.of(2026, 3, 1, 10, 0))
                .build();

        testResult = TestResult.builder()
                .id(resultId)
                .test(psychTest)
                .session(session)
                .status(TestResultStatus.SCORED)
                .scoredAt(LocalDateTime.of(2026, 3, 1, 10, 1))
                .focusLossCount(0)
                .build();
    }

    // ── getTestDetails ────────────────────────────────────────────────────────

    @Test
    void getTestDetails_returnsDto_whenAssigned() {
        when(testRepository.findById(testId)).thenReturn(Optional.of(psychTest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(true);
        // countActiveBySurveyIds returns [surveyId, 5L]
        List<Object[]> countResult = new ArrayList<>();
        countResult.add(new Object[]{surveyId, 5L});
        when(questionRepository.countActiveByFormIds(any())).thenReturn(countResult);

        CandidateTestDto dto = service.getTestDetails(testId, userId);

        assertThat(dto.testId()).isEqualTo(testId);
        assertThat(dto.formId()).isEqualTo(surveyId);
        assertThat(dto.name()).isEqualTo("Big-Five Inventory");
        assertThat(dto.instructions()).isEqualTo("Answer honestly.");
        assertThat(dto.testType()).isEqualTo("PERSONALITY");
        assertThat(dto.timeLimitSecs()).isNull();
        assertThat(dto.questionCount()).isEqualTo(5);
    }

    @Test
    void getTestDetails_throws403_whenNoAssignment() {
        when(testRepository.findById(testId)).thenReturn(Optional.of(psychTest));
        when(userRepository.findById(userId)).thenReturn(Optional.of(candidateUser));
        when(assignmentRepository.hasVisibleAssignment(surveyId, userId, ORG_PATH)).thenReturn(false);

        assertThatThrownBy(() -> service.getTestDetails(testId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getTestDetails_throws404_whenTestNotFound() {
        when(testRepository.findById(testId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTestDetails(testId, userId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── getMyResults ──────────────────────────────────────────────────────────

    @Test
    void getMyResults_returnsEmptyList_whenNoResults() {
        when(resultRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(List.of());

        List<CandidateTestResultDto> results = service.getMyResults(userId);

        assertThat(results).isEmpty();
    }

    @Test
    void getMyResults_mapsStatusAndNames() {
        when(resultRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(List.of(testResult));

        List<CandidateTestResultDto> results = service.getMyResults(userId);

        assertThat(results).hasSize(1);
        CandidateTestResultDto dto = results.get(0);
        assertThat(dto.resultId()).isEqualTo(resultId);
        assertThat(dto.testId()).isEqualTo(testId);
        assertThat(dto.testName()).isEqualTo("Big-Five Inventory");
        assertThat(dto.testType()).isEqualTo("PERSONALITY");
        assertThat(dto.status()).isEqualTo(TestResultStatus.SCORED);
    }

    // ── getResultDetail ───────────────────────────────────────────────────────

    @Test
    void getResultDetail_throws403_whenNotOwner() {
        UUID otherId = UUID.randomUUID();
        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));

        assertThatThrownBy(() -> service.getResultDetail(resultId, otherId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getResultDetail_returnsEmptyScales_whenBreakdownHidden() {
        ResultVisibilityPolicy policy = ResultVisibilityPolicy.builder()
                .test(psychTest)
                .audience(ResultAudience.CANDIDATE)
                .showRawScore(false)
                .showStenProfile(false)
                .showPercentile(false)
                .showScaleBreakdown(false)
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.CANDIDATE))
                .thenReturn(Optional.of(policy));
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of());

        CandidateTestResultDetailsDto dto = service.getResultDetail(resultId, userId);

        assertThat(dto.scales()).isEmpty();
        assertThat(dto.rawScoreVisible()).isFalse();
        assertThat(dto.stenProfileVisible()).isFalse();
        assertThat(dto.percentileVisible()).isFalse();
        assertThat(dto.scaleBreakdownVisible()).isFalse();
    }

    // ── getTeamResultDetail ───────────────────────────────────────────────────

    @Test
    void getTeamResultDetail_throws403_whenUserNotInManagerOrgScope() {
        UUID managerId = UUID.randomUUID();
        OrganizationalUnit managerOrg = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path("/hq/finance").build();
        User manager = User.builder().id(managerId).orgUnit(managerOrg).build();

        // Candidate is in /hq/engineering — not under /hq/finance
        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.getTeamResultDetail(resultId, managerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getTeamResultDetail_appliesManagerPolicy_nullsStenScore() {
        UUID managerId = UUID.randomUUID();
        OrganizationalUnit managerOrg = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path("/hq").build();
        User manager = User.builder().id(managerId).orgUnit(managerOrg).build();

        // Candidate is at /hq/engineering — under /hq
        ResultVisibilityPolicy managerPolicy = ResultVisibilityPolicy.builder()
                .test(psychTest)
                .audience(ResultAudience.MANAGER)
                .showRawScore(false)
                .showStenProfile(false)
                .showPercentile(false)
                .showScaleBreakdown(false)
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.MANAGER))
                .thenReturn(Optional.of(managerPolicy));
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of());

        CandidateTestResultDetailsDto dto = service.getTeamResultDetail(resultId, managerId);

        assertThat(dto.resultId()).isEqualTo(resultId);
        assertThat(dto.stenProfileVisible()).isFalse();
        assertThat(dto.rawScoreVisible()).isFalse();
        assertThat(dto.scales()).isEmpty();
    }

    @Test
    void getTeamResultDetail_appliesManagerPolicy_samePath() {
        UUID managerId = UUID.randomUUID();
        // Manager and candidate share the exact same org path
        OrganizationalUnit sharedOrg = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path(ORG_PATH).build();
        User manager = User.builder().id(managerId).orgUnit(sharedOrg).build();

        // Rebuild testResult with a session user at the same path
        ResponseSession samePathSession = ResponseSession.builder()
                .id(UUID.randomUUID())
                .form(survey)
                .user(candidateUser)   // candidateUser.orgUnit.path == ORG_PATH
                .completedAt(LocalDateTime.of(2026, 3, 1, 10, 0))
                .build();
        TestResult samePathResult = TestResult.builder()
                .id(resultId)
                .test(psychTest)
                .session(samePathSession)
                .status(TestResultStatus.SCORED)
                .scoredAt(LocalDateTime.of(2026, 3, 1, 10, 1))
                .focusLossCount(0)
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(samePathResult));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.MANAGER))
                .thenReturn(Optional.empty());
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of());

        // Should NOT throw — same org path is considered in-scope
        CandidateTestResultDetailsDto dto = service.getTeamResultDetail(resultId, managerId);
        assertThat(dto.resultId()).isEqualTo(resultId);
    }

    @Test
    void getResultDetail_masksScoreFields_perPolicy() {
        ResultVisibilityPolicy policy = ResultVisibilityPolicy.builder()
                .test(psychTest)
                .audience(ResultAudience.CANDIDATE)
                .showRawScore(true)
                .showStenProfile(false)
                .showPercentile(false)
                .showScaleBreakdown(true)
                .build();

        PsychometricScale scale = PsychometricScale.builder()
                .id(UUID.randomUUID()).name("Openness").build();
        ScaleScore scaleScore = ScaleScore.builder()
                .id(UUID.randomUUID())
                .result(testResult)
                .scale(scale)
                .rawScore(new BigDecimal("32.000"))
                .stenScore(7)
                .percentile(new BigDecimal("75.00"))
                .zScore(new BigDecimal("0.600"))
                .itemsAnswered(10)
                .itemsTotal(10)
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.CANDIDATE))
                .thenReturn(Optional.of(policy));
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of(scaleScore));

        CandidateTestResultDetailsDto dto = service.getResultDetail(resultId, userId);

        assertThat(dto.rawScoreVisible()).isTrue();
        assertThat(dto.stenProfileVisible()).isFalse();
        assertThat(dto.scales()).hasSize(1);

        var ss = dto.scales().get(0);
        assertThat(ss.rawScore()).isEqualByComparingTo(new BigDecimal("32.000"));
        assertThat(ss.stenScore()).isNull();      // showStenProfile=false
        assertThat(ss.percentile()).isNull();     // showPercentile=false
        assertThat(ss.zScore()).isNull();         // tied to showStenProfile=false
    }

    // ── competency masking ────────────────────────────────────────────────────

    @Test
    void toResultDetails_competencyMapVisible_true_loadsCompetencies() {
        ResultVisibilityPolicy policy = ResultVisibilityPolicy.builder()
                .test(psychTest)
                .audience(ResultAudience.CANDIDATE)
                .showRawScore(false)
                .showStenProfile(false)
                .showPercentile(false)
                .showScaleBreakdown(false)
                .showCompetencyMap(true)
                .build();

        Competency competency = Competency.builder()
                .id(UUID.randomUUID()).name("Leadership").displayOrder(1).build();
        CompetencyScore compScore = CompetencyScore.builder()
                .id(UUID.randomUUID())
                .result(testResult)
                .competency(competency)
                .score(new BigDecimal("7.500"))
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.CANDIDATE))
                .thenReturn(Optional.of(policy));
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of());
        when(competencyScoreRepository.findByResultIdWithCompetency(resultId))
                .thenReturn(List.of(compScore));

        CandidateTestResultDetailsDto dto = service.getResultDetail(resultId, userId);

        assertThat(dto.competencyMapVisible()).isTrue();
        assertThat(dto.competencies()).hasSize(1);
        assertThat(dto.competencies().get(0).name()).isEqualTo("Leadership");
        assertThat(dto.competencies().get(0).score()).isEqualByComparingTo(new BigDecimal("7.500"));
    }

    @Test
    void toResultDetails_competencyMapVisible_false_returnsEmptyList() {
        ResultVisibilityPolicy policy = ResultVisibilityPolicy.builder()
                .test(psychTest)
                .audience(ResultAudience.CANDIDATE)
                .showRawScore(false)
                .showStenProfile(false)
                .showPercentile(false)
                .showScaleBreakdown(false)
                .showCompetencyMap(false)
                .build();

        when(resultRepository.findByIdWithSessionAndTest(resultId)).thenReturn(Optional.of(testResult));
        when(policyRepository.findByTestIdAndAudience(testId, ResultAudience.CANDIDATE))
                .thenReturn(Optional.of(policy));
        when(scaleScoreRepository.findByResultIdWithScale(resultId)).thenReturn(List.of());

        CandidateTestResultDetailsDto dto = service.getResultDetail(resultId, userId);

        assertThat(dto.competencyMapVisible()).isFalse();
        assertThat(dto.competencies()).isEmpty();
        // competencyScoreRepository must NOT be queried when map is hidden
        verify(competencyScoreRepository, never()).findByResultIdWithCompetency(any());
    }

    // ── getTeamResults ────────────────────────────────────────────────────────

    @Test
    void getTeamResults_returnsResultsInScope() {
        UUID managerId = UUID.randomUUID();
        OrganizationalUnit managerOrgUnit = OrganizationalUnit.builder()
                .id(UUID.randomUUID()).path("/hq").build();
        User manager = User.builder().id(managerId).orgUnit(managerOrgUnit).build();

        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));
        when(resultRepository.findByOrgPathPrefix(eq("/hq"), any(Pageable.class)))
                .thenReturn(List.of(testResult));

        List<TestResultSummaryDto> results = service.getTeamResults(managerId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).testId()).isEqualTo(testId);
        assertThat(results.get(0).testName()).isEqualTo("Big-Five Inventory");
        assertThat(results.get(0).status()).isEqualTo(TestResultStatus.SCORED);
    }

    @Test
    void getTeamResults_throws403_whenManagerHasNoOrgUnit() {
        UUID managerId = UUID.randomUUID();
        User manager = User.builder().id(managerId).orgUnit(null).build();
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.getTeamResults(managerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(resultRepository, never()).findByOrgPathPrefix(any(), any());
    }

    @Test
    void getTeamResults_throws403_whenManagerNotFound() {
        UUID managerId = UUID.randomUUID();
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTeamResults(managerId))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
