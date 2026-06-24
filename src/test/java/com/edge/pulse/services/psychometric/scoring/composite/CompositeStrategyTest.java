package com.edge.pulse.services.psychometric.scoring.composite;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CompositeStrategyTest {
    UUID parent = UUID.randomUUID();
    ScaleScoreResult child(BigDecimal sten, BigDecimal t) {
        return new ScaleScoreResult(UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.ZERO, sten, t, null, 1, 1);
    }

    @Test
    void meanOfChildStens_roundsHalfEvenOneDecimal() {
        // CA_overall = mean(7.0, 8.0, 6.0, 5.0) = 6.5
        var children = List.of(
            child(new BigDecimal("7.0"), null), child(new BigDecimal("8.0"), null),
            child(new BigDecimal("6.0"), null), child(new BigDecimal("5.0"), null));
        ScaleScoreResult out = CompositeStrategies.of(CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN)
                .combine(parent, CompositeBasis.STEN, children);
        assertThat(out.stenScore()).isEqualByComparingTo("6.5");
    }

    @Test
    void meanOfChildTScores_setsTScore() {
        // IQ_overall = mean(60, 70, 50, 40) = 55.0 (T basis)
        var children = List.of(
            child(null, new BigDecimal("60")), child(null, new BigDecimal("70")),
            child(null, new BigDecimal("50")), child(null, new BigDecimal("40")));
        ScaleScoreResult out = CompositeStrategies.of(CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN)
                .combine(parent, CompositeBasis.TSCORE, children);
        assertThat(out.tScore()).isEqualByComparingTo("55.0");
    }
}
