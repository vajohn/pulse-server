package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import java.util.EnumMap;
import java.util.Map;

public final class ItemStrategies {
    private static final Map<ItemStrategyType, ItemStrategy> REGISTRY = new EnumMap<>(ItemStrategyType.class);
    static {
        for (ItemStrategy s : new ItemStrategy[]{
                new LikertValueStrategy(), new BinaryForcedChoiceStrategy(),
                new AnswerKeySingleStrategy(), new AnswerKeyMultipleStrategy(),
                new AdjectiveCountStrategy(), new OptionTaggedTallyStrategy()}) {
            REGISTRY.put(s.type(), s);
        }
    }
    private ItemStrategies() {}
    public static ItemStrategy of(ItemStrategyType type) {
        ItemStrategy s = REGISTRY.get(type);
        if (s == null) throw new IllegalArgumentException("No item strategy for " + type);
        return s;
    }
}
