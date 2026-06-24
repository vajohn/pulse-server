package com.edge.pulse.services.psychometric.scoring.composite;

import com.edge.pulse.data.enums.CompositeMethod;
import java.util.EnumMap;
import java.util.Map;

public final class CompositeStrategies {
    private static final Map<CompositeMethod, CompositeStrategy> REGISTRY = new EnumMap<>(CompositeMethod.class);
    static {
        CompositeStrategy mean = new AggregateOfChildrenStrategy(CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN);
        CompositeStrategy sum  = new AggregateOfChildrenStrategy(CompositeMethod.AGGREGATE_OF_CHILDREN_SUM);
        REGISTRY.put(mean.method(), mean);
        REGISTRY.put(sum.method(), sum);
    }
    private CompositeStrategies() {}
    public static CompositeStrategy of(CompositeMethod m) {
        CompositeStrategy s = REGISTRY.get(m);
        if (s == null) throw new IllegalArgumentException("No composite strategy for " + m);
        return s;
    }
}
