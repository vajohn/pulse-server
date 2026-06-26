package com.edge.pulse.mappers.psychometric;

import com.edge.pulse.data.dto.psychometric.TestApprovalRequestDto;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestApprovalMapperTest {

    private final TestApprovalMapper mapper = new TestApprovalMapper();

    @Test
    void mapsEntityToDto() {
        UUID testId = UUID.randomUUID();
        UUID submitterId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        Form form = Form.builder().id(UUID.randomUUID()).build();
        User submitter = User.builder().id(submitterId).displayName("Submitter One").build();
        PsychometricTest test = PsychometricTest.builder()
                .id(testId).name("Test One").testType(TestType.PERSONALITY)
                .status(TestStatus.PENDING_APPROVAL).version(1).form(form).build();

        TestApprovalRequest request = TestApprovalRequest.builder()
                .id(requestId)
                .test(test)
                .testVersion(1)
                .submittedBy(submitter)
                .status(TestApprovalStatus.PENDING)
                .build();

        TestApprovalRequestDto dto = mapper.toDto(request);

        assertThat(dto.id()).isEqualTo(requestId);
        assertThat(dto.testId()).isEqualTo(testId);
        assertThat(dto.testName()).isEqualTo("Test One");
        assertThat(dto.testVersion()).isEqualTo(1);
        assertThat(dto.submittedById()).isEqualTo(submitterId);
        assertThat(dto.submittedByName()).isEqualTo("Submitter One");
        assertThat(dto.submittedAt()).isNotNull();
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.reviewedById()).isNull();
        assertThat(dto.reviewedByName()).isNull();
        assertThat(dto.reviewedAt()).isNull();
        assertThat(dto.approvalReference()).isNull();
        assertThat(dto.reviewComment()).isNull();
    }

    @Test
    void mapsApprovedEntityWithReviewedBy() {
        UUID reviewerId = UUID.randomUUID();
        Form form = Form.builder().id(UUID.randomUUID()).build();
        User submitter = User.builder().id(UUID.randomUUID()).displayName("Sub").build();
        User reviewer = User.builder().id(reviewerId).displayName("Rev").build();
        PsychometricTest test = PsychometricTest.builder()
                .id(UUID.randomUUID()).name("T").testType(TestType.PERSONALITY)
                .status(TestStatus.ACTIVE).version(1).form(form).build();

        TestApprovalRequest request = TestApprovalRequest.builder()
                .id(UUID.randomUUID())
                .test(test)
                .testVersion(1)
                .submittedBy(submitter)
                .reviewedBy(reviewer)
                .reviewedAt(LocalDateTime.now())
                .approvalReference("email 2026-06-26")
                .status(TestApprovalStatus.APPROVED)
                .build();

        TestApprovalRequestDto dto = mapper.toDto(request);

        assertThat(dto.reviewedById()).isEqualTo(reviewerId);
        assertThat(dto.reviewedByName()).isEqualTo("Rev");
        assertThat(dto.approvalReference()).isEqualTo("email 2026-06-26");
        assertThat(dto.status()).isEqualTo("APPROVED");
    }
}
