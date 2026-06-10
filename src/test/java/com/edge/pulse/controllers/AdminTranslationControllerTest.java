package com.edge.pulse.controllers;

import com.edge.pulse.configs.GlobalExceptionHandler;
import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.translation.CachingTranslationService;
import com.edge.pulse.services.translation.TranslationRateGuard;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AdminTranslationController}.
 *
 * <p>Uses standalone MockMvc — {@code @PreAuthorize} is NOT enforced here.
 * Auth guard coverage lives in JwtAuthenticationFilterTest and the security config.
 * We inject a UUID-principal authentication token directly to simulate a logged-in user.
 */
@ExtendWith(MockitoExtension.class)
class AdminTranslationControllerTest {

    @Mock private CachingTranslationService translationService;
    @Mock private TranslationRateGuard rateGuard;
    @Mock private AuditService auditService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final TranslationProperties translationProperties = new TranslationProperties();

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Authentication token with UUID principal matching controller's {@code (UUID) auth.getPrincipal()}. */
    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminTranslationController(
                        translationService, rateGuard, auditService, translationProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── Single translate ───────────────────────────────────────────────────────

    @Test
    void translate_success_cacheMiss_returns200WithCachedFalse() throws Exception {
        when(translationService.translateSingle("Hello", "en", "ar"))
                .thenReturn(new CachingTranslationService.TranslationResult("مرحبا", false));
        when(translationService.getProvider()).thenReturn(TranslationProvider.AZURE);
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "Hello",
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedText").value("مرحبا"))
                .andExpect(jsonPath("$.provider").value("AZURE"))
                .andExpect(jsonPath("$.cached").value(false));
    }

    @Test
    void translate_success_cacheHit_returnsCachedTrue() throws Exception {
        when(translationService.translateSingle("Hello", "en", "ar"))
                .thenReturn(new CachingTranslationService.TranslationResult("مرحبا", true));
        when(translationService.getProvider()).thenReturn(TranslationProvider.AZURE);
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "Hello",
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));
    }

    @Test
    void translate_blankText_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "",
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void translate_unsupportedLocale_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "Hello",
                        "fromLocale", "en",
                        "toLocale", "fr"   // unsupported
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_locale"));
    }

    @Test
    void translate_budgetExceeded_returns429() throws Exception {
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(false);

        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "Hello",
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("daily_char_budget_exceeded"));
    }

    @Test
    void translate_providerNone_returns200WithNoneProvider() throws Exception {
        when(translationService.translateSingle(any(), any(), any()))
                .thenReturn(new CachingTranslationService.TranslationResult("Hello", false));
        when(translationService.getProvider()).thenReturn(TranslationProvider.NONE);
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/admin/translate")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "text", "Hello",
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("NONE"));
    }

    // ── Batch translate ────────────────────────────────────────────────────────

    @Test
    void translateBatch_success_returns200() throws Exception {
        when(translationService.translateBatch(List.of("Hello", "Goodbye"), "en", "ar"))
                .thenReturn(List.of("مرحبا", "مع السلامة"));
        when(translationService.getProvider()).thenReturn(TranslationProvider.AZURE);
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(true);

        mockMvc.perform(post("/api/admin/translate/batch")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "texts", List.of("Hello", "Goodbye"),
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.translatedTexts[0]").value("مرحبا"))
                .andExpect(jsonPath("$.translatedTexts[1]").value("مع السلامة"))
                .andExpect(jsonPath("$.provider").value("AZURE"));
    }

    @Test
    void translateBatch_emptyList_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/translate/batch")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "texts", List.of(),
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void translateBatch_budgetExceeded_returns429() throws Exception {
        when(rateGuard.tryConsume(any(UUID.class), anyInt())).thenReturn(false);

        mockMvc.perform(post("/api/admin/translate/batch")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "texts", List.of("Hello"),
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void translateBatch_tooManyItems_returns400() throws Exception {
        // 51 items exceeds the @Size(max = 50) constraint
        List<String> tooMany = java.util.Collections.nCopies(51, "Hello");
        mockMvc.perform(post("/api/admin/translate/batch")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "texts", tooMany,
                        "fromLocale", "en",
                        "toLocale", "ar"
                ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void translateBatch_unsupportedLocale_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/translate/batch")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                        "texts", List.of("Hello"),
                        "fromLocale", "en",
                        "toLocale", "de"   // unsupported
                ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_locale"));
    }
}
