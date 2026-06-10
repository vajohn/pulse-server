package com.edge.pulse.strategy;

import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.AnswerSubmission;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.answer.AnswerText;
import com.edge.pulse.repositories.answer.AnswerTextRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.PiiRedactionService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextAnswerStrategyTest {

    @Mock
    private AnswerTextRepository answerTextRepository;
    @Mock
    private PiiRedactionService piiRedactionService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TextAnswerStrategy strategy;

    @Test
    void supportedType_returnsText() {
        assertThat(strategy.supportedType()).isEqualTo(QuestionType.TEXT);
    }

    @Test
    void persist_savesAnswerText() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.TEXT, "my answer", null, null, null, null, null, null, null);
        when(piiRedactionService.redact(anyString()))
                .thenReturn(new PiiRedactionService.ScanResult("my answer", false));
        when(answerTextRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        strategy.persist(submission, request);

        ArgumentCaptor<AnswerText> captor = ArgumentCaptor.forClass(AnswerText.class);
        verify(answerTextRepository).save(captor.capture());
        assertThat(captor.getValue().getValue()).isEqualTo("my answer");
    }

    @Test
    void persist_throwsWhenTextBlank() {
        var submission = AnswerSubmission.builder().id(UUID.randomUUID()).build();
        var request = new SubmitAnswerRequest(UUID.randomUUID(), QuestionType.TEXT, "", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> strategy.persist(submission, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void load_returnsAnswerText() {
        var submissionId = UUID.randomUUID();
        var submission = AnswerSubmission.builder().id(submissionId).build();
        var answerText = AnswerText.builder().value("loaded").build();
        when(answerTextRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(answerText));

        Object result = strategy.load(submission);
        assertThat(result).isEqualTo(answerText);
    }
}
