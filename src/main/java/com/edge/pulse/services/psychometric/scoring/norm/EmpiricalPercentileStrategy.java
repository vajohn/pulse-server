package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.NormConfig;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.math.BigDecimal;
import java.util.UUID;

public class EmpiricalPercentileStrategy implements NormStrategy {
    public NormStrategyType type() { return NormStrategyType.EMPIRICAL_PERCENTILE; }
    public ScaleScoreResult standardize(UUID scaleId, BigDecimal raw, int ans, int tot, NormConfig n) {
        BigDecimal pct = null, sten = null;
        if (n.buckets() != null) {
            for (NormConfig.PercentileBucket b : n.buckets()) {
                if (raw.compareTo(b.rawMin()) >= 0 && raw.compareTo(b.rawMax()) <= 0) {
                    pct = b.percentile(); sten = b.sten(); break;
                }
            }
        }
        return new ScaleScoreResult(scaleId, raw, null, sten, null, pct, ans, tot);
    }
}
