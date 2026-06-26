package com.edge.pulse.mappers.psychometric;

import com.edge.pulse.data.dto.psychometric.TestApprovalRequestDto;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.TestApprovalRequest;
import org.springframework.stereotype.Component;

/**
 * Maps {@link TestApprovalRequest} entities to {@link TestApprovalRequestDto} read objects.
 * Mirrors the {@code RoleChangeMapper} pattern.
 */
@Component
public class TestApprovalMapper {

    public TestApprovalRequestDto toDto(TestApprovalRequest r) {
        User reviewed = r.getReviewedBy();
        return new TestApprovalRequestDto(
                r.getId(),
                r.getTest().getId(),
                r.getTest().getName(),
                r.getTestVersion(),
                r.getSubmittedBy().getId(),
                r.getSubmittedBy().getDisplayName(),
                r.getSubmittedAt(),
                r.getStatus().name(),
                reviewed != null ? reviewed.getId() : null,
                reviewed != null ? reviewed.getDisplayName() : null,
                r.getReviewedAt(),
                r.getApprovalReference(),
                r.getReviewComment()
        );
    }
}
