package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestApprovalServiceReviseTest {

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

    private PsychometricTest buildActiveTest() {
        Form form = Form.builder().id(UUID.randomUUID()).build();
        return PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Test One")
                .description("description")
                .instructions("instructions")
                .testType(TestType.PERSONALITY)
                .status(TestStatus.ACTIVE)
                .version(1)
                .form(form)
                .build();
    }

    @Test
    void reviseClonesActiveIntoNewDraftVersion() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        PsychometricTest clone = approvalService.revise(prior.getId(), userId);

        assertThat(clone.getStatus()).isEqualTo(TestStatus.DRAFT);
        assertThat(clone.getVersion()).isEqualTo(prior.getVersion() + 1);
        assertThat(clone.getSupersedes()).isSameAs(prior);
        assertThat(clone.getName()).isEqualTo(prior.getName());
        assertThat(clone.getTestType()).isEqualTo(prior.getTestType());
        assertThat(clone.getCreatedBy().getId()).isEqualTo(userId);

        // Prior remains ACTIVE (not changed until new version is approved)
        assertThat(prior.getStatus()).isEqualTo(TestStatus.ACTIVE);

        ArgumentCaptor<PsychometricTest> captor = ArgumentCaptor.forClass(PsychometricTest.class);
        verify(testRepository).save(captor.capture());
        PsychometricTest saved = captor.getValue();
        assertThat(saved.getVersion()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(TestStatus.DRAFT);
        assertThat(saved.getSupersedes()).isSameAs(prior);

        verify(auditService).logAction(eq(userId), eq("PSYCH_TEST_REVISED"),
                eq("PsychometricTest"), eq(prior.getId()), any(), isNull());
    }

    @ParameterizedTest
    @EnumSource(value = TestStatus.class, names = {"DRAFT", "PENDING_APPROVAL", "RETIRED"})
    void reviseRejectsNonActive409(TestStatus nonActiveStatus) {
        UUID userId = UUID.randomUUID();
        PsychometricTest test = buildActiveTest();
        test.setStatus(nonActiveStatus);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> approvalService.revise(test.getId(), userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }
}
