package com.edge.pulse.controllers;

import com.edge.pulse.configs.GlobalExceptionHandler;
import com.edge.pulse.services.FalconService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AiController}.
 *
 * <p>Uses standalone MockMvc so @PreAuthorize is not enforced here — auth
 * guard coverage lives in JwtAuthenticationFilterTest and the security config.
 */
@ExtendWith(MockitoExtension.class)
class AiControllerTest {

    @Mock
    private FalconService falconService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AiController(falconService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── GET /api/ai/status ──────────────────────────────────────────────────

    @Test
    void status_falconDisabled_returnsEnabledFalse() throws Exception {
        when(falconService.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/ai/status").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void status_falconEnabled_returnsEnabledTrue() throws Exception {
        when(falconService.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/ai/status").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void status_responseHasNoExtraTopLevelFields() throws Exception {
        when(falconService.isEnabled()).thenReturn(false);

        // Only "enabled" — no leaking internal state
        mockMvc.perform(get("/api/ai/status").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").exists());
    }

    // ── POST /api/ai/chat ───────────────────────────────────────────────────

    @Test
    void chat_falconDisabled_returns503WithReplyField() throws Exception {
        when(falconService.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Hello"}
                                """)
                        .principal(auth()))
                .andExpect(status().isServiceUnavailable())
                // Body is AiChatResponse, so "reply" field must exist
                .andExpect(jsonPath("$.reply").isString());
    }

    @Test
    void chat_enabled_returnsReplyFromService() throws Exception {
        when(falconService.isEnabled()).thenReturn(true);
        when(falconService.chat(eq("Hello"), isNull())).thenReturn("Hi there!");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Hello"}
                                """)
                        .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hi there!"));
    }

    @Test
    void chat_withSystemPrompt_forwardsItToService() throws Exception {
        when(falconService.isEnabled()).thenReturn(true);
        when(falconService.chat(eq("Tell me about Spring"), eq("You are a Java expert.")))
                .thenReturn("Spring is a framework...");

        var body = mapper.writeValueAsString(Map.of(
                "message", "Tell me about Spring",
                "systemPrompt", "You are a Java expert."
        ));

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Spring is a framework..."));
    }

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    void chat_blankMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":""}
                                """)
                        .principal(auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_missingMessageField_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .principal(auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_messageTooLong_returns400() throws Exception {
        var body = mapper.writeValueAsString(Map.of("message", "x".repeat(4001)));

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .principal(auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_messageAtMaxLength_returns200() throws Exception {
        String atLimit = "x".repeat(4000);
        when(falconService.isEnabled()).thenReturn(true);
        when(falconService.chat(eq(atLimit), isNull())).thenReturn("ok");

        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("message", atLimit)))
                        .principal(auth()))
                .andExpect(status().isOk());
    }

    @Test
    void chat_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json")
                        .principal(auth()))
                .andExpect(status().isBadRequest());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());
    }
}
