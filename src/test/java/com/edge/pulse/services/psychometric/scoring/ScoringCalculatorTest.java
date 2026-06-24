package com.edge.pulse.services.psychometric.scoring;

import com.edge.pulse.data.enums.*;
import com.edge.pulse.services.psychometric.scoring.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringCalculatorTest {

    @Test
    void scoresLeafThenChildrenMeanComposite() {
        UUID qa = UUID.randomUUID(), qb = UUID.randomUUID();
        UUID childA = UUID.randomUUID(), childB = UUID.randomUUID(), parent = UUID.randomUUID();

        NormConfig n = new NormConfig(NormStrategyType.PARAMETRIC,
                new BigDecimal("3"), new BigDecimal("1"),
                new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("10"), new BigDecimal("120"), null);

        var scales = List.of(
            new ScaleConfig(childA, "A", parent, ScoreMethod.SUM, null, null, List.of(), n),
            new ScaleConfig(childB, "B", parent, ScoreMethod.SUM, null, null, List.of(), n),
            new ScaleConfig(parent, "P", null, ScoreMethod.SUM,
                    CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN, CompositeBasis.STEN, List.of(childA, childB), null));

        var items = List.of(
            new ItemConfig(qa, childA, ItemStrategyType.LIKERT_VALUE, ScoreDirection.FORWARD, 1.0, null, null, false, null),
            new ItemConfig(qb, childB, ItemStrategyType.LIKERT_VALUE, ScoreDirection.FORWARD, 1.0, null, null, false, null));

        Map<UUID, ItemResponse> responses = Map.of(
            qa, new ItemResponse(qa, 3, 1, 5, null, null, null),  // raw 3 -> z0 -> sten 5.5
            qb, new ItemResponse(qb, 5, 1, 5, null, null, null)); // raw 5 -> z2 -> sten 9.5

        ScoringOutput out = new ScoringCalculator().calculate(new ScoringInput(scales, items, responses));

        Map<UUID, ScaleScoreResult> byScale = new HashMap<>();
        out.scaleScores().forEach(s -> byScale.put(s.scaleId(), s));
        assertThat(byScale.get(childA).stenScore()).isEqualByComparingTo("5.5");
        assertThat(byScale.get(childB).stenScore()).isEqualByComparingTo("9.5");
        // parent = mean(5.5, 9.5) = 7.5
        assertThat(byScale.get(parent).stenScore()).isEqualByComparingTo("7.5");
    }
}
