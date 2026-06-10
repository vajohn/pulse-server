package com.edge.pulse.services;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.repositories.AnswerSubmissionRepository;
import com.edge.pulse.strategy.AnswerPersistenceStrategy;
import com.edge.pulse.strategy.AnswerStrategyResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerSubmissionCreationHelperTest {

    @Mock
    private AnswerSubmissionRepository answerSubmissionRepository;
    @Mock
    private AnswerStrategyResolver strategyResolver;
    @Mock
    private AnswerPersistenceStrategy mockStrategy;

    @InjectMocks
    private AnswerSubmissionCreationHelper helper;

    @Test
    void tryInsert_savesAndPersistsInSameTransaction() {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).startedAt(LocalDateTime.now()).build();
        var question = Question.builder().id(questionId).body("Q?").questionType(QuestionType.TEXT).build();
        var submission = AnswerSubmission.builder()
                .session(session).question(question)
                .answerType(QuestionType.TEXT).version(1).isCurrent(true)
                .submittedAt(LocalDateTime.now()).build();
        var request = new SubmitAnswerRequest(questionId, QuestionType.TEXT, "answer", null, null, null, null, null, null, null);

        when(answerSubmissionRepository.saveAndFlush(any())).thenAnswer(i -> {
            AnswerSubmission s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(strategyResolver.resolve(QuestionType.TEXT)).thenReturn(mockStrategy);

        AnswerSubmission result = helper.tryInsert(submission, request);

        assertThat(result.getId()).isNotNull();
        // persist() is called with the saved submission (which has the DB-assigned ID) so
        // any FK-referencing inserts inside persist() reference a committed row.
        verify(mockStrategy).persist(result, request);
    }
}
