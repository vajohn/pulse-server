package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.NormConfig;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class NormStrategyTest {
    final UUID scale = UUID.randomUUID();

    @Test
    void parametric_computesZStenT() {
        // ATP Agility: M=7.92 SD=3.10 (personality T 10/50/10-120). raw=7.92 -> z=0 -> sten 5.5, T 50.0
        var norm = new NormConfig(NormStrategyType.PARAMETRIC,
                new BigDecimal("7.92"), new BigDecimal("3.10"),
                new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("10"), new BigDecimal("120"), null);
        ScaleScoreResult out = NormStrategies.of(NormStrategyType.PARAMETRIC)
                .standardize(scale, new BigDecimal("7.92"), 1, 1, norm);
        assertThat(out.zScore()).isEqualByComparingTo("0.000");
        assertThat(out.stenScore()).isEqualByComparingTo("5.5");
        assertThat(out.tScore()).isEqualByComparingTo("50.0");
    }

    @Test
    void empiricalPercentile_looksUpBucket() {
        var norm = new NormConfig(NormStrategyType.EMPIRICAL_PERCENTILE,
                null, null, null, null, null, null,
                List.of(new NormConfig.PercentileBucket(
                        new BigDecimal("0"), new BigDecimal("10"),
                        new BigDecimal("42.00"), new BigDecimal("5.0"))));
        ScaleScoreResult out = NormStrategies.of(NormStrategyType.EMPIRICAL_PERCENTILE)
                .standardize(scale, new BigDecimal("7"), 1, 1, norm);
        assertThat(out.percentile()).isEqualByComparingTo("42.00");
        assertThat(out.stenScore()).isEqualByComparingTo("5.0");
        assertThat(out.tScore()).isNull();
    }
}
