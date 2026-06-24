package com.edge.pulse.services.psychometric.scoring.model;

import com.edge.pulse.data.enums.NormStrategyType;
import java.math.BigDecimal;
import java.util.List;

public record NormConfig(
        NormStrategyType strategy,
        BigDecimal mean, BigDecimal sd,                       // PARAMETRIC
        BigDecimal tFactor, BigDecimal tOffset,
        BigDecimal tClipLo, BigDecimal tClipHi,
        List<PercentileBucket> buckets                        // EMPIRICAL_PERCENTILE
) {
    public record PercentileBucket(BigDecimal rawMin, BigDecimal rawMax,
                                   BigDecimal percentile, BigDecimal sten) {}
}
