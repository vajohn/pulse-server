package com.edge.pulse.data.dto.psychometric.imports;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.data.enums.ScoreMethod;

import java.math.BigDecimal;
import java.util.List;

public record ScoringSheetScale(
        String name, String parentName, ScoreMethod scoreMethod,
        NormStrategyType normStrategy, BigDecimal mean, BigDecimal sd,
        BigDecimal tFactor, BigDecimal tOffset, BigDecimal tClipLo, BigDecimal tClipHi,
        CompositeMethod compositeMethod, CompositeBasis compositeBasis,
        List<String> childScaleNames, Integer roundingScale, boolean restricted) {}
