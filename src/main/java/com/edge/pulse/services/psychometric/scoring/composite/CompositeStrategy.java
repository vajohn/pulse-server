package com.edge.pulse.services.psychometric.scoring.composite;

import com.edge.pulse.data.enums.CompositeBasis;
import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import java.util.List;
import java.util.UUID;

public interface CompositeStrategy {
    CompositeMethod method();
    ScaleScoreResult combine(UUID parentScaleId, CompositeBasis basis, List<ScaleScoreResult> children);
}
