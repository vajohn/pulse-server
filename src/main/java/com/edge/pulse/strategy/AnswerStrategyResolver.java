package com.edge.pulse.strategy;

import com.edge.pulse.data.enums.QuestionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AnswerStrategyResolver {
    private final Map<QuestionType, AnswerPersistenceStrategy> strategies;

    public AnswerStrategyResolver(List<AnswerPersistenceStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AnswerPersistenceStrategy::supportedType, Function.identity()));
    }

    public AnswerPersistenceStrategy resolve(QuestionType type) {
        // MULTI_RATING uses the same persistence strategy as RATING.
        // CHOICE_SINGLE is the V18 rename of CHOICE — same strategy, same table.
        QuestionType lookupType = switch (type) {
            case MULTI_RATING -> QuestionType.RATING;
            // CHOICE_SINGLE is the V18 rename of CHOICE — same table, same strategy.
            case CHOICE_SINGLE -> QuestionType.CHOICE;
            // CHOICE_MULTIPLE: each selected option submitted as individual CHOICE row.
            case CHOICE_MULTIPLE -> QuestionType.CHOICE;
            // FORCED_CHOICE: Flutter encodes selection as scaleValue 0 (option-a) or 1 (option-b)
            // with minValue=0 / maxValue=1. Reuse ScaleAnswerStrategy — no candidateAnswerId exists.
            case FORCED_CHOICE -> QuestionType.SCALE;
            // ADJECTIVE_CHECKLIST: dedicated answer_adjective table (V18).
            case ADJECTIVE_CHECKLIST -> QuestionType.ADJECTIVE_CHECKLIST;
            default -> type;
        };
        AnswerPersistenceStrategy strategy = strategies.get(lookupType);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for type: " + type);
        }
        return strategy;
    }
}
