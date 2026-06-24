package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

public class AdjectiveCountStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.ADJECTIVE_COUNT; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.adjectiveCount() == null) return Double.NaN;
        return r.adjectiveCount();
    }
}
