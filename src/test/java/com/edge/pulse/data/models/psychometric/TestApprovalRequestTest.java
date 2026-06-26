package com.edge.pulse.data.models.psychometric;

import com.edge.pulse.data.enums.TestApprovalStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestApprovalRequestTest {
    @Test
    void buildsWithPendingDefaultAndCapturesVersion() {
        TestApprovalRequest r = TestApprovalRequest.builder()
                .testVersion(2)
                .build();
        assertEquals(TestApprovalStatus.PENDING, r.getStatus());
        assertEquals(2, r.getTestVersion());
        assertNotNull(r.getSubmittedAt());
        assertNull(r.getReviewedBy());
        assertNull(r.getReviewedAt());
        assertNull(r.getApprovalReference());
    }
}
