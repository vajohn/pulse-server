package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerScale;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScaleAnswerStrategyTest {

    @Mock
    private AnswerScaleRepository answerScaleRepository;

    @InjectMocks
    private ScaleAnswerStrategy strategy;

    @Test
    void supportedType_returnsScale() {
        assertThat(strategy.supportedType()).isEqualTo(QuestionType.SCALE);
    }

    @Test
    void persist_savesScaleAnswer() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.SCALE, null, 3, 1, 5, null, null, null, null);
        when(answerScaleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        strategy.persist(submission, request);

        ArgumentCaptor<AnswerScale> captor = ArgumentCaptor.forClass(AnswerScale.class);
        verify(answerScaleRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo(3);
        assertThat(captor.getValue().getMinValue()).isEqualTo(1);
        assertThat(captor.getValue().getMaxValue()).isEqualTo(5);
    }

    @Test
    void persist_throwsWhenValueOutOfRange() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.SCALE, null, 10, 1, 5, null, null, null, null);

        assertThatThrownBy(() -> strategy.persist(submission, request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
