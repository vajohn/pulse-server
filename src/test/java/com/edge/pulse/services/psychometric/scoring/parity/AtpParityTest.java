package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import org.junit.jupiter.api.Test;

/**
 * Beacon Red PARITY: the pure {@link ScoringCalculator} must reproduce the vendor Adaptive Traits
 * Profiler (ATP) 2.0 STEN and T-scores exactly (1 dp). Forced-choice items (values 1/2),
 * 11 leaf scales, no composites.
 */
class AtpParityTest {

    @Test
    void reproducesVendorStenAndTscores() {
        ParityAsserter.assertParity("ATP", "atp");
    }
}
