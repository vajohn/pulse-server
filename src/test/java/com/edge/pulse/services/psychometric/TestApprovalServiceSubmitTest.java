package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScaleWeightRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository;
import com.edge.pulse.services.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestApprovalServiceSubmitTest {

    @Mock private PsychometricTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private FormRepository formRepository;
    @Mock private TestApprovalRequestRepository approvalRequestRepository;
    @Mock private ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock private NormTableVersionRepository normTableVersionRepository;
    @Mock private PsychometricScaleRepository scaleRepository;
    @Mock private CompetencyScaleWeightRepository competencyScaleWeightRepository;
    @Mock private AuditService auditService;

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

        assertThatThrownBy(() -> approvalService.submit(test.getId(), submitterId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value()).isEqualTo(422));
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
}
