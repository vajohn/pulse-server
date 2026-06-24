package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import org.junit.jupiter.api.Test;

/**
 * Beacon Red PARITY: the pure {@link ScoringCalculator} must reproduce the vendor PTI Plus 2.0
 * STEN and T-scores exactly (1 dp) for every user/scale in the gold deliverable.
 * Pure Likert (recoded 0..3), AGGREGATE_OF_ITEMS composites (CONTROL/COMMITMENT/CHALLENGE/CONFIDENCE)
 * and the Consistency validity scale.
 */
class PtiParityTest {

    @Test
    void reproducesVendorStenAndTscores() {
        ParityAsserter.assertParity("PTI", "pti");
    }
}
