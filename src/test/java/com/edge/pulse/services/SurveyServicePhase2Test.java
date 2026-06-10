package com.edge.pulse.services;

import com.edge.pulse.data.dto.CreateFormRequest;
import com.edge.pulse.data.dto.UpdateQuestionRequest;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Phase 2 additions to SurveyService:
 * listForms(), updateForm(), deleteForm(), getAllQuestions(), updateQuestion().
 * These were previously implemented in AdminSurveyController with direct repo access.
 */
@ExtendWith(MockitoExtension.class)
class SurveyServicePhase2Test {

    @Mock private FormRepository formRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CandidateAnswerRepository candidateAnswerRepository;
    @Mock private FormCacheService cacheService;
    @Mock private FormAssignmentRepository formAssignmentRepository;
    @Mock private UserRepository userRepository;

    private SurveyService surveyService;

    @BeforeEach
    void setUp() {
        surveyService = new SurveyService(formRepository, questionRepository,
                candidateAnswerRepository, cacheService, formAssignmentRepository, userRepository);
    }

    // -----------------------------------------------------------------------
    // listForms
    // -----------------------------------------------------------------------

    @Test
    void listForms_usesFindAllWithQuestionsForEagerLoad() {
        Form s1 = Form.builder().id(UUID.randomUUID()).title("Alpha").build();
        Form s2 = Form.builder().id(UUID.randomUUID()).title("Beta").build();
        when(formRepository.findAllWithQuestions()).thenReturn(List.of(s1, s2));

        List<Form> result = surveyService.listForms();

        assertThat(result).containsExactly(s1, s2);
        verify(formRepository).findAllWithQuestions();
        verify(formRepository, never()).findAll();
    }

    // -----------------------------------------------------------------------
    // updateForm
    // -----------------------------------------------------------------------

    @Test
    void updateForm_updatesFieldsAndSaves() {
        UUID id = UUID.randomUUID();
        Form existing = Form.builder().id(id).title("Old").description("Old desc")
                .anonWindowMinutes(30).build();
        when(formRepository.findByIdWithQuestions(id)).thenReturn(Optional.of(existing));
        when(formRepository.save(existing)).thenReturn(existing);

        CreateFormRequest req = new CreateFormRequest("New Title", "New desc", 60);
        Form result = surveyService.updateForm(id, req);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("New desc");
        assertThat(result.getAnonWindowMinutes()).isEqualTo(60);
        verify(formRepository).save(existing);
    }

    @Test
    void updateForm_nullAnonWindowMinutes_keepsExistingValue() {
        UUID id = UUID.randomUUID();
        Form existing = Form.builder().id(id).title("T").anonWindowMinutes(45).build();
        when(formRepository.findByIdWithQuestions(id)).thenReturn(Optional.of(existing));
        when(formRepository.save(existing)).thenReturn(existing);

        CreateFormRequest req = new CreateFormRequest("T2", null, null);
        surveyService.updateForm(id, req);

        assertThat(existing.getAnonWindowMinutes()).isEqualTo(45);
    }

    @Test
    void updateForm_notFound_throws() {
        when(formRepository.findByIdWithQuestions(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> surveyService.updateForm(UUID.randomUUID(),
                new CreateFormRequest("x", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // deleteForm
    // -----------------------------------------------------------------------

    @Test
    void deleteForm_callsRepositoryDelete() {
        UUID id = UUID.randomUUID();
        Form form = Form.builder().id(id).build();
        when(formRepository.findByIdWithQuestions(id)).thenReturn(Optional.of(form));

        surveyService.deleteForm(id);

        verify(formRepository).delete(form);
    }

    @Test
    void deleteForm_notFound_throws() {
        when(formRepository.findByIdWithQuestions(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> surveyService.deleteForm(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(formRepository, never()).delete(any());
    }

    // -----------------------------------------------------------------------
    // getAllQuestions
    // -----------------------------------------------------------------------

    @Test
    void getAllQuestions_delegatesToRepository() {
        UUID formId = UUID.randomUUID();
        Question q1 = Question.builder().id(UUID.randomUUID()).displayOrder(1).build();
        Question q2 = Question.builder().id(UUID.randomUUID()).displayOrder(2).build();
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(formId))
                .thenReturn(List.of(q1, q2));

        List<Question> result = surveyService.getAllQuestions(formId);

        assertThat(result).containsExactly(q1, q2);
    }

    // -----------------------------------------------------------------------
    // updateQuestion
    // -----------------------------------------------------------------------

    @Test
    void updateQuestion_updatesFieldsAndSaves() {
        UUID formId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        Form form = Form.builder().id(formId).build();
        Question question = Question.builder()
                .id(questionId).form(form)
                .body("Old body").questionType(QuestionType.SCALE).displayOrder(1)
                .build();

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));
        when(questionRepository.save(question)).thenReturn(question);

        UpdateQuestionRequest req = new UpdateQuestionRequest("New body", null, QuestionType.TEXT, 2, null, null, null, null, null, null, null, null, null, null);
        Question result = surveyService.updateQuestion(formId, questionId, req);

        assertThat(result.getBody()).isEqualTo("New body");
        assertThat(result.getQuestionType()).isEqualTo(QuestionType.TEXT);
        assertThat(result.getDisplayOrder()).isEqualTo(2);
        verify(questionRepository).save(question);
    }

    @Test
    void updateQuestion_wrongForm_throws() {
        UUID formId = UUID.randomUUID();
        UUID otherFormId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        Form otherForm = Form.builder().id(otherFormId).build();
        Question question = Question.builder().id(questionId).form(otherForm).build();

        when(questionRepository.findById(questionId)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> surveyService.updateQuestion(formId, questionId,
                new UpdateQuestionRequest("body", null, null, 1, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to form");
        verify(questionRepository, never()).save(any());
    }

    @Test
    void updateQuestion_notFound_throws() {
        when(questionRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> surveyService.updateQuestion(UUID.randomUUID(), UUID.randomUUID(),
                new UpdateQuestionRequest("body", null, null, 1, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Question not found");
    }
}
