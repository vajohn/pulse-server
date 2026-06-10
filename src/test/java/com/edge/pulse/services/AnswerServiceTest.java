package com.edge.pulse.services;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.*;
import com.edge.pulse.mappers.AnswerMapper;
import com.edge.pulse.repositories.AnswerSubmissionRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.strategy.AnswerStrategyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerServiceTest {

    @Mock
    private AnswerSubmissionRepository answerSubmissionRepository;
    @Mock
    private ResponseSessionRepository responseSessionRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private AnswerStrategyResolver strategyResolver;
    @Mock
    private AnswerMapper answerMapper;
    @Mock
    private PiiRedactionService piiRedactionService;
    @Mock
    private AnswerSubmissionCreationHelper answerSubmissionCreationHelper;

    @InjectMocks
    private AnswerService service;

    @Test
    void submitAnswer_createsNewSubmission() {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).startedAt(LocalDateTime.now()).build();
        var question = Question.builder().id(questionId).body("Q?").questionType(QuestionType.TEXT).build();

        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(answerSubmissionRepository.findBySessionIdAndQuestionIdAndIsCurrentTrue(sessionId, questionId)).thenReturn(Optional.empty());
        when(answerSubmissionCreationHelper.tryInsert(any(), any())).thenAnswer(i -> {
            AnswerSubmission s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(answerMapper.toDto(any())).thenReturn(new AnswerDto(UUID.randomUUID(), questionId, QuestionType.TEXT, 1, true, LocalDateTime.now(), "answer", null, null, null, null, null, null, null));

        var request = new SubmitAnswerRequest(questionId, QuestionType.TEXT, "my answer", null, null, null, null, null, null, null);
        AnswerDto result = service.submitAnswer(sessionId, request);

        assertThat(result).isNotNull();
        verify(answerSubmissionCreationHelper).tryInsert(any(), eq(request));
    }

    @Test
    void submitAnswer_throwsWhenSessionCompleted() {
        var sessionId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).startedAt(LocalDateTime.now()).completedAt(LocalDateTime.now()).build();
        when(responseSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.TEXT, "answer", null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.submitAnswer(sessionId, request))
                .isInstanceOf(IllegalStateException.class);
    }
}
