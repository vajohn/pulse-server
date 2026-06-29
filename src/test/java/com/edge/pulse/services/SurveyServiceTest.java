package com.edge.pulse.services;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyServiceTest {

    @Mock FormRepository formRepository;
    @Mock QuestionRepository questionRepository;
    @Mock CandidateAnswerRepository candidateAnswerRepository;
    @Mock FormCacheService cacheService;
    @Mock FormAssignmentRepository formAssignmentRepository;
    @Mock UserRepository userRepository;

    private SurveyService service() {
        return new SurveyService(formRepository, questionRepository, candidateAnswerRepository,
                cacheService, formAssignmentRepository, userRepository);
    }

    private static AddQuestionRequest choiceWithOption(CandidateAnswerDto option) {
        return new AddQuestionRequest(
                "Which figure comes next?", null, QuestionType.CHOICE_SINGLE,
                null, null, 0,
                List.of(option),
                null, null,
                null, null,
                null, null,
                null, null,
                null);
    }

    @Test
    void addQuestion_persistsOptionImage_andAllowsEmptyLabel() {
        UUID formId = UUID.randomUUID();
        UUID img = UUID.randomUUID();
        Form form = Form.builder().id(formId).build();
        form.setQuestions(new ArrayList<>());

        when(formRepository.findByIdWithQuestions(formId)).thenReturn(Optional.of(form));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        // image-only option: null label + an EN image asset id
        AddQuestionRequest req = choiceWithOption(
                CandidateAnswerDto.of(null, null, null, 0, img, null));

        service().addQuestion(formId, req);

        ArgumentCaptor<CandidateAnswer> cap = ArgumentCaptor.forClass(CandidateAnswer.class);
        verify(candidateAnswerRepository, times(1)).save(cap.capture());
        CandidateAnswer saved = cap.getValue();
        assertThat(saved.getImageAssetId()).isEqualTo(img);
        assertThat(saved.getImageAssetIdAr()).isNull();
        assertThat(saved.getLabel()).isEqualTo("");   // empty, never null
    }

    @Test
    void addQuestion_persistsBothLocaleImages() {
        UUID formId = UUID.randomUUID();
        UUID en = UUID.randomUUID();
        UUID ar = UUID.randomUUID();
        Form form = Form.builder().id(formId).build();
        form.setQuestions(new ArrayList<>());

        when(formRepository.findByIdWithQuestions(formId)).thenReturn(Optional.of(form));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        AddQuestionRequest req = choiceWithOption(
                CandidateAnswerDto.of(null, "A", "أ", 0, en, ar));

        service().addQuestion(formId, req);

        ArgumentCaptor<CandidateAnswer> cap = ArgumentCaptor.forClass(CandidateAnswer.class);
        verify(candidateAnswerRepository).save(cap.capture());
        CandidateAnswer saved = cap.getValue();
        assertThat(saved.getImageAssetId()).isEqualTo(en);
        assertThat(saved.getImageAssetIdAr()).isEqualTo(ar);
        assertThat(saved.getLabel()).isEqualTo("A");
    }
}
