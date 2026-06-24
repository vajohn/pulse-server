package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

public class OptionTaggedTallyStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.OPTION_TAGGED_TALLY; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.selectedTagScaleId() == null) return Double.NaN;
        // The calculator only calls this for the item whose scale == selected tag.
        return item.scaleId().equals(r.selectedTagScaleId()) ? 1.0 : 0.0;
    }
}
