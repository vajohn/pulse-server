package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import java.util.EnumMap;
import java.util.Map;

public final class NormStrategies {
    private static final Map<NormStrategyType, NormStrategy> REGISTRY = new EnumMap<>(NormStrategyType.class);
    static {
        for (NormStrategy s : new NormStrategy[]{new ParametricNormStrategy(), new EmpiricalPercentileStrategy()})
            REGISTRY.put(s.type(), s);
    }
    private NormStrategies() {}
    public static NormStrategy of(NormStrategyType t) {
        NormStrategy s = REGISTRY.get(t);
        if (s == null) throw new IllegalArgumentException("No norm strategy for " + t);
        return s;
    }
}
