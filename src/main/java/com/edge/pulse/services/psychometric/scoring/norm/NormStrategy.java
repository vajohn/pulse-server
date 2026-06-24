package com.edge.pulse.services.psychometric.scoring.norm;

import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.NormConfig;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.math.BigDecimal;
import java.util.UUID;

public interface NormStrategy {
    NormStrategyType type();
    ScaleScoreResult standardize(UUID scaleId, BigDecimal raw, int itemsAnswered, int itemsTotal, NormConfig norm);
}
