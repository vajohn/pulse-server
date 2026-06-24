package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.services.psychometric.NormStandardizer;
import com.edge.pulse.services.psychometric.scoring.model.NormConfig;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.math.BigDecimal;
import java.util.UUID;

public class ParametricNormStrategy implements NormStrategy {
    public NormStrategyType type() { return NormStrategyType.PARAMETRIC; }
    public ScaleScoreResult standardize(UUID scaleId, BigDecimal raw, int ans, int tot, NormConfig n) {
        BigDecimal z = NormStandardizer.zScore(raw, n.mean(), n.sd());
        BigDecimal sten = NormStandardizer.stenDecimal(z);
        BigDecimal t = NormStandardizer.tScore(z, n.tFactor(), n.tOffset(), n.tClipLo(), n.tClipHi());
        BigDecimal pct = NormStandardizer.percentile(z);
        return new ScaleScoreResult(scaleId, raw, z, sten, t, pct, ans, tot);
    }
}
