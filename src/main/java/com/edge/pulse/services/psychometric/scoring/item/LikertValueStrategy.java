package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

public class LikertValueStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.LIKERT_VALUE; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.scaleValue() == null) return Double.NaN;
        int v = r.scaleValue();
        if (item.direction() == ScoreDirection.REVERSE) v = r.scaleMax() + r.scaleMin() - v;
        return v;
    }
}
