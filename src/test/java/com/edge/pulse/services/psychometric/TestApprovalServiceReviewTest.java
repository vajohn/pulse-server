package com.edge.pulse.services.psychometric;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestApprovalServiceReviewTest {

    @Mock private PsychometricTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private FormRepository formRepository;
    @Mock private TestApprovalRequestRepository approvalRequestRepository;
    @Mock private ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock private NormTableVersionRepository normTableVersionRepository;
    @Mock private PsychometricScaleRepository scaleRepository;
    @Mock private CompetencyScaleWeightRepository competencyScaleWeightRepository;
    @Mock private com.edge.pulse.mappers.psychometric.TestApprovalMapper approvalMapper;
    @Mock private AuditService auditService;

    @InjectMocks
    private TestApprovalService approvalService;

    private UUID submitterId = UUID.randomUUID();
    private UUID reviewerId = UUID.randomUUID();

    private PsychometricTest buildPendingTest() {
        Form form = Form.builder().id(UUID.randomUUID()).build();
        return PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Test One")
                .testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL)
                .version(1)
                .form(form)
                .build();
    }

    private TestApprovalRequest buildPendingRequest(PsychometricTest test) {
        User submitter = User.builder().id(submitterId).build();
        return TestApprovalRequest.builder()
                .id(UUID.randomUUID())
                .test(test)
                .testVersion(test.getVersion())
                .submittedBy(submitter)
                .status(TestApprovalStatus.PENDING)
                .build();
    }

    @Test
    void approveActivatesTestAndRetiresSupersededPrior() {
        // prior ACTIVE test that will be superseded
        PsychometricTest prior = PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Prior")
                .testType(TestType.PERSONALITY)
                .status(TestStatus.ACTIVE)
                .version(1)
                .form(Form.builder().id(UUID.randomUUID()).build())
                .build();

        PsychometricTest test = buildPendingTest();
        test.setVersion(2);
        test.setSupersedes(prior);

        TestApprovalRequest request = buildPendingRequest(test);
        User reviewer = User.builder().id(reviewerId).build();

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        TestApprovalRequest result = approvalService.review(reviewerId, request.getId(),
                "APPROVE", "email 2026-06-26", null);

        assertThat(result.getStatus()).isEqualTo(TestApprovalStatus.APPROVED);
        assertThat(test.getStatus()).isEqualTo(TestStatus.ACTIVE);
        assertThat(prior.getStatus()).isEqualTo(TestStatus.RETIRED);
        assertThat(result.getApprovalReference()).isEqualTo("email 2026-06-26");
        assertThat(result.getReviewedBy().getId()).isEqualTo(reviewerId);

        verify(testRepository).save(prior);  // prior RETIRED in same tx
        verify(auditService).logAction(eq(reviewerId), eq("PSYCH_TEST_APPROVED"),
                eq("PsychometricTest"), eq(test.getId()), any(), isNull());
    }

    @Test
    void approveWithoutSupersedesJustActivates() {
        PsychometricTest test = buildPendingTest();
        TestApprovalRequest request = buildPendingRequest(test);
        User reviewer = User.builder().id(reviewerId).build();

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        TestApprovalRequest result = approvalService.review(reviewerId, request.getId(),
                "APPROVE", "email 2026-06-26", null);

        assertThat(result.getStatus()).isEqualTo(TestApprovalStatus.APPROVED);
        assertThat(test.getStatus()).isEqualTo(TestStatus.ACTIVE);
        // No prior to retire — testRepository.save called once (for the test, not a prior)
        verify(testRepository, times(1)).save(test);
    }

    @Test
    void rejectReturnsToDraftAndRequiresComment() {
        PsychometricTest test = buildPendingTest();
        TestApprovalRequest request = buildPendingRequest(test);
        User reviewer = User.builder().id(reviewerId).build();

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
        when(userRepository.findById(reviewerId)).thenReturn(Optional.of(reviewer));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(approvalRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any())).thenReturn("{}");

        TestApprovalRequest result = approvalService.review(reviewerId, request.getId(),
                "REJECT", null, "Missing norm table");

        assertThat(result.getStatus()).isEqualTo(TestApprovalStatus.REJECTED);
        assertThat(test.getStatus()).isEqualTo(TestStatus.DRAFT);
        assertThat(result.getReviewComment()).isEqualTo("Missing norm table");

        verify(auditService).logAction(eq(reviewerId), eq("PSYCH_TEST_REJECTED"),
                eq("PsychometricTest"), eq(test.getId()), any(), isNull());
    }

    @Test
    void rejectWithBlankCommentThrows400() {
        PsychometricTest test = buildPendingTest();
        TestApprovalRequest request = buildPendingRequest(test);

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> approvalService.review(reviewerId, request.getId(),
                "REJECT", null, ""))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void selfApprovalIsForbidden403() {
        PsychometricTest test = buildPendingTest();
        TestApprovalRequest request = buildPendingRequest(test);
        // reviewerId == submitterId
        UUID sameId = submitterId;

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> approvalService.review(sameId, request.getId(),
                "APPROVE", "ref", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void reviewNonPendingRequest409() {
        PsychometricTest test = buildPendingTest();
        TestApprovalRequest request = buildPendingRequest(test);
        request.setStatus(TestApprovalStatus.APPROVED);

        when(approvalRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> approvalService.review(reviewerId, request.getId(),
                "APPROVE", "ref", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
