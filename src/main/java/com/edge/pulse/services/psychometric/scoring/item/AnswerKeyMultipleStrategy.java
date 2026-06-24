package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AnswerKeyMultipleStrategy implements ItemStrategy {
    public ItemStrategyType type() { return ItemStrategyType.ANSWER_KEY_MULTIPLE; }
    public double score(ItemConfig item, ItemResponse r) {
        if (r == null || r.selectedAnswerIds() == null || r.selectedAnswerIds().isEmpty()) return Double.NaN;
        if (item.correctAnswerIds() == null || item.correctAnswerIds().isEmpty()) return 0.0;
        Set<UUID> selected = new HashSet<>(r.selectedAnswerIds());
        Set<UUID> key = new HashSet<>(item.correctAnswerIds());
        if (!item.partialCredit()) return selected.equals(key) ? 1.0 : 0.0;
        long correct = selected.stream().filter(key::contains).count();
        long incorrect = selected.stream().filter(id -> !key.contains(id)).count();
        return Math.max(0.0, correct - 0.25 * incorrect);
    }
}
