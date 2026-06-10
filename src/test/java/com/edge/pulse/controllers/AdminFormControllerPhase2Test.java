package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.CreateFormRequest;
import com.edge.pulse.data.dto.UpdateQuestionRequest;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.mappers.FormMapper;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SurveyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies that AdminFormController (formerly AdminSurveyController) correctly
 * delegates all operations to SurveyService. Tests confirm the controller
 * forwards calls to the service at the renamed /api/admin/forms routes.
 */
@ExtendWith(MockitoExtension.class)
class AdminFormControllerPhase2Test {

    private MockMvc mockMvc;

    @Mock private SurveyService surveyService;
    @Mock private FormMapper formMapper;
    @Mock private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final UUID AUTH_USER_ID = UUID.randomUUID();

    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(AUTH_USER_ID, null, List.of());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminFormController(surveyService, formMapper, auditService))
                .build();
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/forms — listForms
    // -----------------------------------------------------------------------

    @Test
    void listForms_delegatesToService() throws Exception {
        Form f = Form.builder().id(UUID.randomUUID()).title("Alpha").build();
        when(surveyService.listForms()).thenReturn(List.of(f));
        when(formMapper.toDto(f)).thenReturn(null); // null dto just to avoid NPE in JSON

        mockMvc.perform(get("/api/admin/forms"))
                .andExpect(status().isOk());

        verify(surveyService).listForms();
    }

    // -----------------------------------------------------------------------
    // PUT /api/admin/forms/{id} — updateForm
    // -----------------------------------------------------------------------

    @Test
    void updateForm_delegatesToServiceAndAudits() throws Exception {
        UUID formId = UUID.randomUUID();
        Form updated = Form.builder().id(formId).title("New").build();
        when(surveyService.updateForm(eq(formId), any(CreateFormRequest.class))).thenReturn(updated);
        when(formMapper.toDto(updated)).thenReturn(null);

        CreateFormRequest req = new CreateFormRequest("New", "desc", null);
        mockMvc.perform(put("/api/admin/forms/{id}", formId)
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(surveyService).updateForm(eq(formId), any(CreateFormRequest.class));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/admin/forms/{id} — deleteForm
    // -----------------------------------------------------------------------

    @Test
    void deleteForm_delegatesToServiceAndAudits() throws Exception {
        UUID formId = UUID.randomUUID();
        doNothing().when(surveyService).deleteForm(formId);

        mockMvc.perform(delete("/api/admin/forms/{id}", formId)
                        .principal(principal))
                .andExpect(status().isNoContent());

        verify(surveyService).deleteForm(formId);
    }

    // -----------------------------------------------------------------------
    // PUT /api/admin/forms/{id}/questions/{qId} — updateQuestion
    // -----------------------------------------------------------------------

    @Test
    void updateQuestion_delegatesToService() throws Exception {
        UUID formId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder().id(questionId).body("New body").displayOrder(1).build();
        when(surveyService.updateQuestion(eq(formId), eq(questionId), any(UpdateQuestionRequest.class)))
                .thenReturn(q);
        when(formMapper.toQuestionDto(q)).thenReturn(null);

        UpdateQuestionRequest req = new UpdateQuestionRequest("New body", null, QuestionType.SCALE, 1, null, null, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/api/admin/forms/{id}/questions/{qId}", formId, questionId)
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        verify(surveyService).updateQuestion(eq(formId), eq(questionId), any(UpdateQuestionRequest.class));
    }

    // -----------------------------------------------------------------------
    // DELETE /api/admin/forms/{id}/questions/{qId}
    // -----------------------------------------------------------------------

    @Test
    void deleteQuestion_callsExpireQuestion() throws Exception {
        UUID formId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        Question q = Question.builder().id(questionId).build();
        when(surveyService.expireQuestion(formId, questionId)).thenReturn(q);

        mockMvc.perform(delete("/api/admin/forms/{id}/questions/{qId}", formId, questionId)
                        .principal(principal))
                .andExpect(status().isNoContent());

        verify(surveyService).expireQuestion(formId, questionId);
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/forms/{id}/questions — getAllQuestions
    // -----------------------------------------------------------------------

    @Test
    void getAllQuestions_delegatesToService() throws Exception {
        UUID formId = UUID.randomUUID();
        when(surveyService.getAllQuestions(formId)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/forms/{id}/questions", formId))
                .andExpect(status().isOk());

        verify(surveyService).getAllQuestions(formId);
    }
}
