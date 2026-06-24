package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

public interface ItemStrategy {
    ItemStrategyType type();
    /** Contribution to the scale, or Double.NaN if unanswered. */
    double score(ItemConfig item, ItemResponse response);
}
