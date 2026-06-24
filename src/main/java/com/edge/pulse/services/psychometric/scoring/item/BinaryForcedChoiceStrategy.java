package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

/** ATP: value in {1,2}. FORWARD 1->1,2->0 ; REVERSE 1->0,2->1. */
public class BinaryForcedChoiceStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.BINARY_FORCED_CHOICE; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.scaleValue() == null) return Double.NaN;
        int v = r.scaleValue();              // 1 or 2
        double fwd = (v == 1) ? 1.0 : 0.0;
        return item.direction() == ScoreDirection.REVERSE ? (1.0 - fwd) : fwd;
    }
}
