package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;

public class AnswerKeySingleStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.ANSWER_KEY_SINGLE; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.selectedAnswerIds() == null || r.selectedAnswerIds().isEmpty()) return Double.NaN;
        if (item.correctAnswerId() == null) return Double.NaN;
        return item.correctAnswerId().equals(r.selectedAnswerIds().get(0)) ? 1.0 : 0.0;
    }
}
