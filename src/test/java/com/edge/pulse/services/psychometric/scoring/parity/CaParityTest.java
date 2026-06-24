package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import org.junit.jupiter.api.Test;

/**
 * Beacon Red PARITY: the pure {@link ScoringCalculator} must reproduce the vendor Cognitive
 * Assessment (CA) STEN and T-scores. Answer-key sub-scales (verbal/numerical/attention-to-detail
 * are CA.a 15-item; logical is CA.b 20-item) merged per user across four response files, with
 * cognitive T scaling (15/100/40..160), CA_overall = STEN-mean and IQ_overall = T-mean.
 */
class CaParityTest {

    @Test
    void reproducesVendorStenAndTscores() {
        ParityAsserter.assertParity("CA", "ca");
    }
}
