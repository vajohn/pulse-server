package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.dto.psychometric.UpdatePsychometricTestRequest;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.mappers.FormMapper;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.NormEntryRepository;
import com.edge.pulse.repositories.psychometric.NormScaleParamRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.PsychometricAnalyticsMvRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for the ACTIVE scoring-field lock on PsychometricAdminService.
 * ACTIVE tests must reject scoring-affecting edits (409) but allow name/description/instructions.
 */
@ExtendWith(MockitoExtension.class)
class PsychometricAdminServiceLockTest {

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
    @Mock private NormScaleParamRepository normScaleParamRepository;
    @Mock private InstrumentService instrumentService;
    @Mock private TestApprovalRequestRepository testApprovalRequestRepository;
    @Mock private com.edge.pulse.mappers.psychometric.TestApprovalMapper testApprovalMapper;

    @InjectMocks
    private PsychometricAdminService adminService;

    private PsychometricTest activeTest() {
        Form form = Form.builder().id(UUID.randomUUID()).build();
        return PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Active Test")
                .testType(TestType.PERSONALITY)
                .status(TestStatus.ACTIVE)
                .version(1)
                .form(form)
                .build();
    }

    // ── saveScoringKey on ACTIVE → 409 ───────────────────────────────────────

    @Test
    void activeTestRejectsScoringKeyEdit409() {
        PsychometricTest test = activeTest();
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> adminService.saveScoringKey(test.getId(), List.of(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── saveNormTable on ACTIVE → 409 ────────────────────────────────────────

    @Test
    void activeTestRejectsNormTableEdit409() {
        PsychometricTest test = activeTest();
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> adminService.saveNormTable(test.getId(), List.of(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── createScale on ACTIVE → 409 ──────────────────────────────────────────

    @Test
    void activeTestRejectsScaleCreate409() {
        PsychometricTest test = activeTest();
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        com.edge.pulse.data.dto.psychometric.CreateScaleRequest req =
                new com.edge.pulse.data.dto.psychometric.CreateScaleRequest(
                        "NewScale", null, "SUM", null, 0, null, null, null, false);

        assertThatThrownBy(() -> adminService.createScale(test.getId(), req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── updateScale on ACTIVE → 409 ──────────────────────────────────────────

    @Test
    void activeTestRejectsScaleUpdate409() {
        PsychometricTest test = activeTest();
        UUID scaleId = UUID.randomUUID();
        PsychometricScale scale = PsychometricScale.builder()
                .id(scaleId).test(test).name("S").build();
        when(scaleRepository.findById(scaleId)).thenReturn(Optional.of(scale));

        com.edge.pulse.data.dto.psychometric.UpdateScaleRequest req =
                new com.edge.pulse.data.dto.psychometric.UpdateScaleRequest(
                        "NewName", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> adminService.updateScale(test.getId(), scaleId, req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── deleteScale on ACTIVE → 409 ──────────────────────────────────────────

    @Test
    void activeTestRejectsScaleDelete409() {
        PsychometricTest test = activeTest();
        UUID scaleId = UUID.randomUUID();
        PsychometricScale scale = PsychometricScale.builder()
                .id(scaleId).test(test).name("S").build();
        when(scaleRepository.findById(scaleId)).thenReturn(Optional.of(scale));

        assertThatThrownBy(() -> adminService.deleteScale(test.getId(), scaleId, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── timeLimitSecs change on ACTIVE → 409 ─────────────────────────────────

    @Test
    void activeTestRejectsTimeLimitChange409() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.COGNITIVE)
                .status(TestStatus.ACTIVE).version(1).timeLimitSecs(1800)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        UpdatePsychometricTestRequest req = new UpdatePsychometricTestRequest(null, null, null, 3600, null);

        assertThatThrownBy(() -> adminService.updateTest(test.getId(), req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── name/description edit on ACTIVE → ALLOWED ────────────────────────────

    @Test
    void activeTestAllowsNameAndDescriptionEdit() {
        PsychometricTest test = activeTest();
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(formRepository.countActiveQuestionsByFormId(any())).thenReturn(0L);
        when(testApprovalRequestRepository.findFirstByTestIdAndStatus(any(), any()))
                .thenReturn(Optional.empty());
        when(auditService.buildDetail(any(), any())).thenReturn("{}");

        // Only name/description/instructions — no scoring-affecting fields
        UpdatePsychometricTestRequest req =
                new UpdatePsychometricTestRequest("New Name", "New desc", null, null, null);

        // Should NOT throw
        var dto = adminService.updateTest(test.getId(), req, UUID.randomUUID());
        assertThat(dto.name()).isEqualTo("New Name");
    }

    // ── guard is no-op on DRAFT ──────────────────────────────────────────────

    @Test
    void draftTestAllowsScoringKeyEdit() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.DRAFT).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(userRepository.findById(any())).thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).build()));
        when(scoringKeyVersionRepository.deprecateActiveKeysByTestId(any())).thenReturn(0);
        when(scoringKeyVersionRepository.findMaxVersionByTestId(any())).thenReturn(Optional.of(0));
        when(scoringKeyVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        // DRAFT → should NOT throw; passes through to normal save-scoring-key logic
        var result = adminService.saveScoringKey(test.getId(), List.of(), UUID.randomUUID());
        assertThat(result).isNotNull();
    }

    // ── I5: saveScoringKey on PENDING_APPROVAL → 409 ─────────────────────────

    @Test
    void pendingApprovalTestRejectsScoringKeyEdit409() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> adminService.saveScoringKey(test.getId(), List.of(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── I5: saveNormTable on PENDING_APPROVAL → 409 ──────────────────────────

    @Test
    void pendingApprovalTestRejectsNormTableEdit409() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> adminService.saveNormTable(test.getId(), List.of(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── I5: createScale on PENDING_APPROVAL → 409 ────────────────────────────

    @Test
    void pendingApprovalTestRejectsScaleCreate409() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        com.edge.pulse.data.dto.psychometric.CreateScaleRequest req =
                new com.edge.pulse.data.dto.psychometric.CreateScaleRequest(
                        "NewScale", null, "SUM", null, 0, null, null, null, false);

        assertThatThrownBy(() -> adminService.createScale(test.getId(), req, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── I3: archiveTest on PENDING_APPROVAL → 409 ────────────────────────────

    @Test
    void archivePendingApprovalTestRejects409() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> adminService.archiveTest(test.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).containsIgnoringCase("approval request");
                });
    }

    // ── I3: archiveTest on ACTIVE → RETIRED (allowed) ────────────────────────

    @Test
    void archiveActiveTestSucceeds() {
        PsychometricTest test = activeTest();
        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any())).thenReturn("{}");

        adminService.archiveTest(test.getId(), UUID.randomUUID());

        assertThat(test.getStatus()).isEqualTo(TestStatus.RETIRED);
    }

    // ── I3: archiveTest on DRAFT → RETIRED (allowed) ─────────────────────────

    @Test
    void archiveDraftTestSucceeds() {
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.DRAFT).version(1)
                .form(Form.builder().id(UUID.randomUUID()).build()).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any())).thenReturn("{}");

        adminService.archiveTest(test.getId(), UUID.randomUUID());

        assertThat(test.getStatus()).isEqualTo(TestStatus.RETIRED);
    }
}
