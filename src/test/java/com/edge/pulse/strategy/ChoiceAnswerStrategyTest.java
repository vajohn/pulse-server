package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerChoice;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoiceAnswerStrategyTest {

    @Mock
    private AnswerChoiceRepository answerChoiceRepository;
    @Mock
    private CandidateAnswerRepository candidateAnswerRepository;

    @InjectMocks
    private ChoiceAnswerStrategy strategy;

    @Test
    void supportedType_returnsChoice() {
        assertThat(strategy.supportedType()).isEqualTo(QuestionType.CHOICE);
    }

    @Test
    void persist_savesChoiceAnswer() {
        var candidateId = UUID.randomUUID();
        var candidate = CandidateAnswer.builder().id(candidateId).label("Option A").build();
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.CHOICE, null, null, null, null, candidateId, null, null, null);

        when(candidateAnswerRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
        when(answerChoiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        strategy.persist(submission, request);

        ArgumentCaptor<AnswerChoice> captor = ArgumentCaptor.forClass(AnswerChoice.class);
        verify(answerChoiceRepository).save(captor.capture());
        assertThat(captor.getValue().getCandidateAnswer().getId()).isEqualTo(candidateId);
    }

    @Test
    void persist_throwsWhenCandidateNotFound() {
        var candidateId = UUID.randomUUID();
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.CHOICE, null, null, null, null, candidateId, null, null, null);

        when(candidateAnswerRepository.findById(candidateId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> strategy.persist(submission, request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
