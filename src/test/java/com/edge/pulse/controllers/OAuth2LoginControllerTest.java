package com.edge.pulse.controllers;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.models.User;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.MicrosoftGraphService;
import com.edge.pulse.services.SfRoleDerivationService;
import com.edge.pulse.services.UserProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginControllerTest {

    @Mock private UserProvisioningService userProvisioningService;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private MicrosoftGraphService graphService;
    @Mock private OAuth2AuthorizedClientService authorizedClientService;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ObjectMapper objectMapper;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SfRoleDerivationService sfRoleDerivationService;
    @Mock private AuditService auditService;
    private final CacheTtlProperties cacheTtlProps = new CacheTtlProperties();

    private OAuth2LoginController controller;

    @BeforeEach
    void setUp() {
        controller = new OAuth2LoginController(
                userProvisioningService, jwtTokenService, graphService,
                authorizedClientService, stringRedisTemplate, objectMapper,
                sfRoleDerivationService, auditService, cacheTtlProps);
        ReflectionTestUtils.setField(controller, "deepLinkScheme", "com.edge.pulse");
        // Lenient: dedup-only tests don't touch Redis ops — suppress strict Mockito check.
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── dedup guard ──────────────────────────────────────────────────────────────

    /**
     * Duplicate /login/success (e.g. Chrome Custom Tab retry): setIfAbsent returns
     * false (another request owns the slot) and the real exchange code is already
     * stored. No new JWT tokens must be issued.
     */
    @Test
    void onLoginSuccess_duplicateRequest_reusesExistingCodeWithoutIssuingTokens() throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("azure-sub-123");

        // First request already claimed the dedup slot
        when(valueOps.setIfAbsent(eq("oauth_code_dedup:azure-sub-123"), eq("pending"), any(Duration.class)))
                .thenReturn(false);
        // Real exchange code already written by the first request
        when(valueOps.get("oauth_code_dedup:azure-sub-123")).thenReturn("existingcode123");

        StringWriter body = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        controller.onLoginSuccess(oidcUser, mock(HttpServletRequest.class), response);

        // No new tokens must be generated for the duplicate request
        verify(jwtTokenService, never()).generateAccessToken(any());
        verify(jwtTokenService, never()).generateRefreshToken(any(), any(), any());

        // HTML must embed the existing code and point at the correct deep-link scheme
        String html = body.toString();
        assertThat(html).contains("existingcode123");
        assertThat(html).contains("com.edge.pulse://auth/callback?code=existingcode123");
    }

    /**
     * Dedup key exists but still holds "pending" (< 1 ms race between two requests
     * arriving essentially simultaneously). The controller falls through and runs
     * the full provisioning flow rather than blocking.
     */
    @Test
    void onLoginSuccess_pendingRace_fallsThroughToProvisioning() throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("azure-sub-456");

        // setIfAbsent returns false → another request owns the slot
        when(valueOps.setIfAbsent(eq("oauth_code_dedup:azure-sub-456"), eq("pending"), any(Duration.class)))
                .thenReturn(false);
        // But it hasn't written the real code yet — still "pending"
        when(valueOps.get("oauth_code_dedup:azure-sub-456")).thenReturn("pending");

        // Full flow must run — stop early at provisioning to avoid wiring the entire chain
        when(userProvisioningService.provisionOrUpdateUser(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("intentional stop — provisioning reached"));

        try {
            controller.onLoginSuccess(oidcUser, mock(HttpServletRequest.class), mock(HttpServletResponse.class));
        } catch (RuntimeException ignored) {
            // Expected — we only need to confirm provisioning was attempted
        }

        verify(userProvisioningService).provisionOrUpdateUser(
                eq("azure-sub-456"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * Redis unavailable — setIfAbsent returns null. The guard must fail-open:
     * the full provisioning flow runs so login is not blocked by a Redis outage.
     */
    @Test
    void onLoginSuccess_redisUnavailable_setIfAbsentReturnsNull_failsOpen() throws Exception {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("azure-sub-789");

        // Redis down — setIfAbsent returns null
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(null);

        // Full flow must run
        when(userProvisioningService.provisionOrUpdateUser(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("intentional stop — provisioning reached"));

        try {
            controller.onLoginSuccess(oidcUser, mock(HttpServletRequest.class), mock(HttpServletResponse.class));
        } catch (RuntimeException ignored) {
            // Expected
        }

        verify(userProvisioningService).provisionOrUpdateUser(
                eq("azure-sub-789"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        // The get() call must NOT be made — we only call get() when acquired is false
        verify(valueOps, never()).get(anyString());
    }

    // ── happy-path / audit log ──────────────────────────────────────────────────

    /**
     * Successful first-time login: dedup slot acquired, SF role derivation used
     * (no Entra roles), provisioning succeeds, tokens issued, and — critically —
     * auditService.logAction() is called exactly once with action="ROLE_DERIVE_SF".
     *
     * <p>This test is the compliance guard for the audit requirement: a future
     * refactor that accidentally removes or misplaces the audit call will fail here.
     */
    @Test
    void onLoginSuccess_happyPath_emitsAuditLogAfterProvisioning() throws Exception {
        UUID userId = UUID.randomUUID();

        // Minimal OidcUser stub — no Entra roles → SF derivation path
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("azure-sub-happy");
        when(oidcUser.getEmail()).thenReturn("test@edge.ae");

        // Dedup: first request acquires the slot
        when(valueOps.setIfAbsent(eq("oauth_code_dedup:azure-sub-happy"), eq("pending"), any(Duration.class)))
                .thenReturn(true);

        // SF role derivation returns the 5-role baseline
        Set<String> derivedRoles = Set.of(
                "SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER");
        when(sfRoleDerivationService.deriveRolesFromSfProfile(eq("azure-sub-happy"), any(), any()))
                .thenReturn(derivedRoles);

        // Provisioning succeeds
        User provisionedUser = mock(User.class);
        when(provisionedUser.getId()).thenReturn(userId);
        when(provisionedUser.getEmail()).thenReturn("test@edge.ae");
        when(userProvisioningService.provisionOrUpdateUser(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(provisionedUser);

        // Audit detail builder
        when(auditService.buildDetail(eq("roles"), anyString())).thenReturn("{\"roles\":\"[...]\"}");

        // Token generation
        when(jwtTokenService.generateAccessToken(provisionedUser)).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(eq(provisionedUser), any(), any()))
                .thenReturn("refresh-token");

        // ObjectMapper serialises the token pair for Redis storage
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"at\":\"access-token\",\"rt\":\"refresh-token\"}");

        // Capture rendered HTML
        StringWriter body = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.onLoginSuccess(oidcUser, request, response);

        // Audit log must be called exactly once with the correct action and userId
        verify(auditService).logAction(
                eq(userId),
                eq("ROLE_DERIVE_SF"),
                eq("user"),
                eq(userId),
                anyString(),
                isNull());

        // Tokens must be generated
        verify(jwtTokenService).generateAccessToken(provisionedUser);
        verify(jwtTokenService).generateRefreshToken(eq(provisionedUser), any(), any());

        // Deep-link HTML must be rendered
        assertThat(body.toString()).contains("com.edge.pulse://auth/callback?code=");
    }

    /**
     * Entra-supplied roles path: OidcUser carries non-empty roles claim.
     * Audit action must be "ROLE_PROVISION_ENTRA" — NOT "ROLE_DERIVE_SF".
     * Verifies that the conditional action name is correct for both paths.
     */
    @Test
    void onLoginSuccess_entraRolesPresent_emitsRoleProvisionEntraAuditAction() throws Exception {
        UUID userId = UUID.randomUUID();

        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("azure-sub-entra");
        when(oidcUser.getEmail()).thenReturn("hr@edge.ae");
        // Entra supplies roles — SF derivation must NOT be called
        when(oidcUser.getClaimAsStringList("roles")).thenReturn(List.of("HR_FULL_CRUD"));

        when(valueOps.setIfAbsent(eq("oauth_code_dedup:azure-sub-entra"), eq("pending"), any(Duration.class)))
                .thenReturn(true);

        User provisionedUser = mock(User.class);
        when(provisionedUser.getId()).thenReturn(userId);
        when(provisionedUser.getEmail()).thenReturn("hr@edge.ae");
        when(userProvisioningService.provisionOrUpdateUser(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(provisionedUser);

        when(auditService.buildDetail(eq("roles"), anyString())).thenReturn("{\"roles\":\"[...]\"}");
        when(jwtTokenService.generateAccessToken(provisionedUser)).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(eq(provisionedUser), any(), any()))
                .thenReturn("refresh-token");
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"at\":\"access-token\",\"rt\":\"refresh-token\"}");

        StringWriter body = new StringWriter();
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("User-Agent")).thenReturn("TestAgent");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        controller.onLoginSuccess(oidcUser, request, response);

        // Action must reflect the Entra source, not SF derivation
        verify(auditService).logAction(
                eq(userId),
                eq("ROLE_PROVISION_ENTRA"),
                eq("user"),
                eq(userId),
                anyString(),
                isNull());

        // SF derivation must NOT have been invoked
        verify(sfRoleDerivationService, never()).deriveRolesFromSfProfile(any(), any(), any());
    }
}
