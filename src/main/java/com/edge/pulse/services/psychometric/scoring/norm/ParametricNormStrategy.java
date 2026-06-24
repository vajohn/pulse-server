package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.services.psychometric.NormStandardizer;
import com.edge.pulse.services.psychometric.scoring.model.NormConfig;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class ParametricNormStrategy implements NormStrategy {
    public NormStrategyType type() { return NormStrategyType.PARAMETRIC; }
    public ScaleScoreResult standardize(UUID scaleId, BigDecimal raw, int ans, int tot, NormConfig n) {
        // High-precision z for the STEN and T formulas (avoids premature 3-dp rounding loss).
        BigDecimal zFull = raw.subtract(n.mean()).divide(n.sd(), 10, RoundingMode.HALF_EVEN);
        BigDecimal sten = NormStandardizer.stenDecimal(zFull);
        BigDecimal t = NormStandardizer.tScore(zFull, n.tFactor(), n.tOffset(), n.tClipLo(), n.tClipHi());
        BigDecimal pct = NormStandardizer.percentile(zFull);
        BigDecimal z = zFull.setScale(3, RoundingMode.HALF_EVEN); // persisted 3-dp z
        return new ScaleScoreResult(scaleId, raw, z, sten, t, pct, ans, tot);
    }
}
