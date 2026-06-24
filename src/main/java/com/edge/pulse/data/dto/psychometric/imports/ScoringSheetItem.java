package com.edge.pulse.data.dto.psychometric.imports;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.ScoreDirection;

public record ScoringSheetItem(
        String questionHeader, String scaleName, ScoreDirection direction,
        ItemStrategyType itemStrategy, double weight, String tagScaleName) {}
