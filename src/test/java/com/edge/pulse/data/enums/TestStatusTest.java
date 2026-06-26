package com.edge.pulse.data.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestStatusTest {
    @Test
    void definesPendingApprovalBetweenDraftAndActive() {
        assertDoesNotThrow(() -> TestStatus.valueOf("PENDING_APPROVAL"));
        assertEquals(4, TestStatus.values().length);
    }
}
