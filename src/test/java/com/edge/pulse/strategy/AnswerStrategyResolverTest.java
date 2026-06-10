package com.edge.pulse.strategy;

import com.edge.pulse.data.enums.QuestionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerStrategyResolverTest {

    @Mock
    private TextAnswerStrategy textStrategy;
    @Mock
    private ScaleAnswerStrategy scaleStrategy;

    @Test
    void resolve_returnsCorrectStrategy() {
        when(textStrategy.supportedType()).thenReturn(QuestionType.TEXT);
        when(scaleStrategy.supportedType()).thenReturn(QuestionType.SCALE);

        var resolver = new AnswerStrategyResolver(List.of(textStrategy, scaleStrategy));

        assertThat(resolver.resolve(QuestionType.TEXT)).isEqualTo(textStrategy);
        assertThat(resolver.resolve(QuestionType.SCALE)).isEqualTo(scaleStrategy);
    }

    @Test
    void resolve_throwsForUnknownType() {
        when(textStrategy.supportedType()).thenReturn(QuestionType.TEXT);
        var resolver = new AnswerStrategyResolver(List.of(textStrategy));

        assertThatThrownBy(() -> resolver.resolve(QuestionType.RATING))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
