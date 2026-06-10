package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.Session;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.SessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.PermissionCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock private JwtTokenService jwtTokenService;
    @Mock private UserRepository userRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private AuditService auditService;
    @Mock private PermissionCacheService permissionCacheService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(
                jwtTokenService, userRepository, sessionRepository,
                auditService, permissionCacheService,
                stringRedisTemplate, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private UsernamePasswordAuthenticationToken authToken(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                userId, "test-token",
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))
        );
    }

    // ── /api/auth/me ─────────────────────────────────────────────────────────────

    @Test
    void me_authenticated_returnsUser() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@edge.com")
                .displayName("Test User")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionCacheService.toUserSummary(user)).thenReturn(
                new UserSummary(userId, "test@edge.com", "Test User", null, List.of(), List.of(), null, null));

        mockMvc.perform(get("/api/auth/me")
                        .principal(authToken(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@edge.com"))
                .andExpect(jsonPath("$.displayName").value("Test User"));
    }

    @Test
    void me_userNotFound_returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/me")
                        .principal(authToken(userId)))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/auth/logout ─────────────────────────────────────────────────────────

    @Test
    void logout_authenticated_returns200() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/auth/logout")
                        .principal(authToken(userId)))
                .andExpect(status().isOk());

        verify(jwtTokenService).blacklistAccessToken("test-token");
    }

    // ── /api/auth/refresh ────────────────────────────────────────────────────────

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"invalid-token\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── /api/auth/exchange (1-I) ─────────────────────────────────────────────────

    @Test
    void exchange_validCode_returnsTokens() throws Exception {
        String code = "abc123";
        String json = "{\"at\":\"access-token-value\",\"rt\":\"refresh-token-value\"}";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("oauth_code:" + code)).thenReturn(json);

        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"));
    }

    @Test
    void exchange_expiredOrConsumedCode_returns401() throws Exception {
        String code = "expired-code";

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("oauth_code:" + code)).thenReturn(null);

        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exchange_missingCodeField_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchange_blankCode_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
