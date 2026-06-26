package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestApprovalServiceSubmitTest {

    @Mock private PsychometricTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private FormRepository formRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CandidateAnswerRepository candidateAnswerRepository;
    @Mock private TestApprovalRequestRepository approvalRequestRepository;
    @Mock private ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock private ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock private ScoringKeyCorrectAnswerRepository scoringKeyCorrectAnswerRepository;
    @Mock private NormTableVersionRepository normTableVersionRepository;
    @Mock private NormEntryRepository normEntryRepository;
    @Mock private PsychometricScaleRepository scaleRepository;
    @Mock private CompetencyScaleWeightRepository competencyScaleWeightRepository;
    @Mock private ResultVisibilityPolicyRepository resultVisibilityPolicyRepository;
    @Mock private com.edge.pulse.mappers.psychometric.TestApprovalMapper approvalMapper;
    @Mock private AuditService auditService;
    @Mock private PsychometricAdminService psychometricAdminService;

    @InjectMocks
    private TestApprovalService approvalService;

    private PsychometricTest buildDraftTest(TestType type) {
        User creator = User.builder().id(UUID.randomUUID()).build();
        Form form = Form.builder().id(UUID.randomUUID()).build();
        return PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Test One")
                .testType(type)
                .status(TestStatus.DRAFT)
                .version(1)
                .form(form)
                .createdBy(creator)
                .build();
    }

    private ScoringKeyVersion activeScoringKey(PsychometricTest test) {
        return ScoringKeyVersion.builder()
                .id(UUID.randomUUID())
                .test(test)
                .version(1)
                .status(ScoringKeyStatus.ACTIVE)
                .build();
    }

    private NormTableVersion validatedNormTable(PsychometricTest test) {
        return NormTableVersion.builder()
                .id(UUID.randomUUID())
                .test(test)
                .version(1)
                .label("Norms 2026")
                .status(NormStatus.VALIDATED)
                .build();
    }

    @Test
    void submitMovesDraftToPendingAndOpensRequest() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);
        User submitter = User.builder().id(submitterId).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(userRepository.findById(submitterId)).thenReturn(Optional.of(submitter));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeScoringKey(test)));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.of(validatedNormTable(test)));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any())).thenReturn("{}");

        TestApprovalRequest result = approvalService.submit(test.getId(), submitterId);

        assertThat(test.getStatus()).isEqualTo(TestStatus.PENDING_APPROVAL);
        assertThat(result.getStatus()).isEqualTo(TestApprovalStatus.PENDING);
        assertThat(result.getTestVersion()).isEqualTo(1);
        assertThat(result.getSubmittedBy().getId()).isEqualTo(submitterId);

        ArgumentCaptor<TestApprovalRequest> captor = ArgumentCaptor.forClass(TestApprovalRequest.class);
        verify(approvalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(TestApprovalStatus.PENDING);

        verify(auditService).logAction(eq(submitterId), eq("PSYCH_TEST_SUBMITTED"),
                eq("PsychometricTest"), eq(test.getId()), any(), isNull());
    }

    @Test
    void submitRejectsUnscoreableWith422_noScoringKey() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        // norm check won't be reached (scoring key fails first), but Mockito strict stubbing requires we don't over-stub

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                    assertThat(rse.getReason()).contains("no active scoring key");
                });
    }

    // I1: keyed test with scoring key but NO validated norm table → 422
    @Test
    void submitRejectsUnscoreableWith422_noNormTable() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeScoringKey(test)));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());   // no validated norm table

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                    assertThat(rse.getReason()).contains("no validated norm table");
                });
    }

    // I1: COMPETENCY test with scale weights (no norm needed) → scoreability passes
    @Test
    void submitCompetencyTestWithWeightsPasses() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.COMPETENCY);
        User submitter = User.builder().id(submitterId).build();

        UUID scaleId = UUID.randomUUID();
        PsychometricScale scale = PsychometricScale.builder().id(scaleId).test(test).name("S").build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scaleRepository.findByTestId(test.getId())).thenReturn(List.of(scale));
        when(competencyScaleWeightRepository.findByScaleIdIn(List.of(scaleId)))
                .thenReturn(List.of(mock(com.edge.pulse.data.models.psychometric.CompetencyScaleWeight.class)));
        when(userRepository.findById(submitterId)).thenReturn(Optional.of(submitter));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any())).thenReturn("{}");

        // Should NOT throw
        TestApprovalRequest result = approvalService.submit(test.getId(), submitterId);
        assertThat(result.getStatus()).isEqualTo(TestApprovalStatus.PENDING);
    }

    // I1: COMPETENCY test with NO scale weights → 422
    @Test
    void submitCompetencyTestWithNoWeights_422() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.COMPETENCY);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scaleRepository.findByTestId(test.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                    assertThat(rse.getReason()).contains("competency scale weights not configured");
                });
    }

    @Test
    void submitRejectsNonDraftWith409() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);
        test.setStatus(TestStatus.ACTIVE);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void submitRejectsWhenAlreadyPending409() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // M1: concurrent duplicate caught via DataIntegrityViolationException → 409
    @Test
    void submitConcurrentDuplicateCaughtAs409() {
        UUID submitterId = UUID.randomUUID();
        PsychometricTest test = buildDraftTest(TestType.PERSONALITY);
        User submitter = User.builder().id(submitterId).build();

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));
        when(userRepository.findById(submitterId)).thenReturn(Optional.of(submitter));
        when(approvalRequestRepository.existsByTestIdAndStatus(test.getId(), TestApprovalStatus.PENDING)).thenReturn(false);
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(activeScoringKey(test)));
        when(normTableVersionRepository.findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.of(validatedNormTable(test)));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("idx_test_approval_open"));

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
