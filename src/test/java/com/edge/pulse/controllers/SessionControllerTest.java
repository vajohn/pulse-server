package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.mappers.SessionMapper;
import com.edge.pulse.services.AnswerService;
import com.edge.pulse.services.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    /**
     * Simulates Spring Security's ExceptionTranslationFilter in standalone MockMvc
     * so that AccessDeniedException thrown by ownership checks produces a 403.
     */
    @RestControllerAdvice
    static class SecurityAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        ResponseEntity<Void> onAccessDenied() {
            return ResponseEntity.status(403).build();
        }
    }

    private MockMvc mockMvc;

    @Mock private SessionService sessionService;
    @Mock private AnswerService answerService;
    @Mock private SessionMapper sessionMapper;

    private static final UUID AUTH_USER_ID = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        principal = new UsernamePasswordAuthenticationToken(AUTH_USER_ID, null, List.of());
        SessionController controller = new SessionController(sessionService, answerService, sessionMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new SecurityAdvice())
                .build();
    }

    // ── getPriorAnswers ─────────────────────────────────────────────────────────

    @Test
    void getPriorAnswers_returns403ForAnonymousSession() throws Exception {
        var sessionId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).isAnonymous(true).startedAt(LocalDateTime.now()).build();
        when(sessionService.getSession(eq(sessionId), eq(AUTH_USER_ID))).thenReturn(session);

        mockMvc.perform(get("/api/sessions/" + sessionId + "/prior-answers").principal(principal))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPriorAnswers_returns200ForIdentifiedSession() throws Exception {
        var sessionId = UUID.randomUUID();
        var session = ResponseSession.builder().id(sessionId).isAnonymous(false).startedAt(LocalDateTime.now()).build();
        when(sessionService.getSession(eq(sessionId), eq(AUTH_USER_ID))).thenReturn(session);
        when(answerService.getPriorAnswers(sessionId)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/sessions/" + sessionId + "/prior-answers").principal(principal))
                .andExpect(status().isOk());
    }

    // ── submitAnswer (1-F ownership check) ──────────────────────────────────────

    @Test
    void submitAnswer_withAuth_returns201() throws Exception {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerDto = new AnswerDto(UUID.randomUUID(), questionId, QuestionType.TEXT, 1, true,
                LocalDateTime.now(), "answer", null, null, null, null, null, null, null);

        // getSession() for ownership check — returns session (ownership verified)
        when(sessionService.getSession(eq(sessionId), eq(AUTH_USER_ID)))
                .thenReturn(ResponseSession.builder().id(sessionId).isAnonymous(false).startedAt(LocalDateTime.now()).build());
        when(answerService.submitAnswer(eq(sessionId), any())).thenReturn(answerDto);

        String body = "{\"questionId\":\"" + questionId + "\",\"answerType\":\"TEXT\",\"textValue\":\"my answer\"}";
        mockMvc.perform(post("/api/sessions/" + sessionId + "/answers")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void submitAnswer_wrongOwner_returns403() throws Exception {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();

        // Ownership check fails — session belongs to a different user
        doThrow(new AccessDeniedException("Session does not belong to the requesting user"))
                .when(sessionService).getSession(eq(sessionId), eq(AUTH_USER_ID));

        String body = "{\"questionId\":\"" + questionId + "\",\"answerType\":\"TEXT\",\"textValue\":\"my answer\"}";
        mockMvc.perform(post("/api/sessions/" + sessionId + "/answers")
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ── updateAnswer (1-F ownership check) ──────────────────────────────────────

    @Test
    void updateAnswer_withAuth_returns200() throws Exception {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerDto = new AnswerDto(UUID.randomUUID(), questionId, QuestionType.SCALE, 2, true,
                LocalDateTime.now(), null, 4, 1, 5, null, null, null, null);

        when(sessionService.getSession(eq(sessionId), eq(AUTH_USER_ID)))
                .thenReturn(ResponseSession.builder().id(sessionId).isAnonymous(false).startedAt(LocalDateTime.now()).build());
        when(answerService.versionAnswer(eq(sessionId), eq(questionId), any())).thenReturn(answerDto);

        String body = "{\"questionId\":\"" + questionId + "\",\"answerType\":\"SCALE\",\"scaleValue\":4,\"minValue\":1,\"maxValue\":5}";
        mockMvc.perform(put("/api/sessions/" + sessionId + "/answers/" + questionId)
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateAnswer_wrongOwner_returns403() throws Exception {
        var sessionId = UUID.randomUUID();
        var questionId = UUID.randomUUID();

        doThrow(new AccessDeniedException("Session does not belong to the requesting user"))
                .when(sessionService).getSession(eq(sessionId), eq(AUTH_USER_ID));

        String body = "{\"questionId\":\"" + questionId + "\",\"answerType\":\"SCALE\",\"scaleValue\":4,\"minValue\":1,\"maxValue\":5}";
        mockMvc.perform(put("/api/sessions/" + sessionId + "/answers/" + questionId)
                        .principal(principal)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }
}
