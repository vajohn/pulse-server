package com.edge.pulse.services.psychometric.scoring.composite;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class AggregateOfChildrenStrategy implements CompositeStrategy {
    private final CompositeMethod method;
    public AggregateOfChildrenStrategy(CompositeMethod method) { this.method = method; }
    public CompositeMethod method() { return method; }

    public ScaleScoreResult combine(UUID parentId, CompositeBasis basis, List<ScaleScoreResult> children, int roundingScale) {
        Function<ScaleScoreResult, BigDecimal> pick =
                basis == CompositeBasis.TSCORE ? ScaleScoreResult::tScore : ScaleScoreResult::stenScore;
        List<BigDecimal> vals = children.stream().map(pick).filter(java.util.Objects::nonNull).toList();
        if (vals.isEmpty()) return new ScaleScoreResult(parentId, null, null, null, null, null, 0, 0);
        BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal agg = method == CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN
                ? sum.divide(BigDecimal.valueOf(vals.size()), 4, RoundingMode.HALF_EVEN)
                : sum;
        BigDecimal rounded = agg.setScale(roundingScale, RoundingMode.HALF_EVEN);
        BigDecimal sten = basis == CompositeBasis.STEN ? rounded : null;
        BigDecimal t = basis == CompositeBasis.TSCORE ? rounded : null;
        return new ScaleScoreResult(parentId, null, null, sten, t, null, children.size(), children.size());
    }
}
