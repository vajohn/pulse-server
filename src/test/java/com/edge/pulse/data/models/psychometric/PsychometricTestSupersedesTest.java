package com.edge.pulse.data.models.psychometric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PsychometricTestSupersedesTest {
    @Test
    void recordsTheSupersededPriorVersion() {
        PsychometricTest prior = PsychometricTest.builder().version(1).build();
        PsychometricTest revised = PsychometricTest.builder().version(2).supersedes(prior).build();
        assertSame(prior, revised.getSupersedes());
        assertNull(prior.getSupersedes());
    }
}
