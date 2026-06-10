package com.edge.pulse.controllers;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.GraphUserProfile;
import com.edge.pulse.data.models.User;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.MicrosoftGraphService;
import com.edge.pulse.services.SfRoleDerivationService;
import com.edge.pulse.services.UserProvisioningService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Controller
@Profile("azure")   // Azure /login/success handler; injects OAuth2AuthorizedClientService (azure-only bean). Absent on k2.
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginController {
    @Value("${pulse.deep-link-scheme}")
    private String deepLinkScheme;

    private final UserProvisioningService userProvisioningService;
    private final JwtTokenService jwtTokenService;
    private final MicrosoftGraphService graphService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final SfRoleDerivationService sfRoleDerivationService;
    private final AuditService auditService;
    private final CacheTtlProperties cacheTtlProps;

    /**
     * Validates that deepLinkScheme is safe for HTML/JS embedding at startup.
     * An unrestricted env var value embedded directly in HTML is an XSS vector.
     * Restrict to lowercase alphanumeric + dots (e.g. "com.edge.pulse").
     */
    @PostConstruct
    void validateConfig() {
        if (!deepLinkScheme.matches("[a-z][a-z0-9.]*")) {
            throw new IllegalStateException(
                "Invalid pulse.deep-link-scheme '" + deepLinkScheme +
                "': must match [a-z][a-z0-9.]* to be safe for HTML embedding");
        }
    }

    @GetMapping("/login/success")
    public void onLoginSuccess(@AuthenticationPrincipal OidcUser oidcUser,
                                HttpServletRequest request,
                                HttpServletResponse response) throws IOException {
        String azureAdId = oidcUser.getSubject();

        // Deduplication guard: Chrome Custom Tab can trigger /login/success twice
        // in rapid succession (~100 ms apart). Without this check, both requests
        // complete independently — each issues a fresh refresh token and a new
        // exchange code, creating an orphaned DB session.
        //
        // Strategy: atomically claim a per-subject slot via SET NX (setIfAbsent).
        //   acquired = true  → this is the first request; proceed with full flow.
        //   acquired = false → a concurrent request owns the slot; reuse its code.
        //   acquired = null  → Redis unavailable; fail-open and run the full flow.
        String dedupKey = "oauth_code_dedup:" + azureAdId;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(dedupKey, "pending", cacheTtlProps.oauthDedupTtl());
        if (Boolean.FALSE.equals(acquired)) {
            // Another request has already completed or is in-flight for this subject.
            // Read back the real exchange code it stored. If the value is still
            // "pending" (< 1 ms race window), fall through and generate a second
            // code rather than blocking — this path is effectively unreachable in
            // normal operation (~100 ms Chrome retry interval).
            String existingCode = stringRedisTemplate.opsForValue().get(dedupKey);
            if (existingCode != null && !existingCode.equals("pending")) {
                log.info("Dedup: suppressed duplicate /login/success for OIDC subject {} "
                        + "— reusing in-flight exchange code, no new tokens issued", azureAdId);
                renderDeepLinkHtml(response, existingCode);
                return;
            }
            log.warn("Dedup: key in 'pending' state for OIDC subject {} — "
                    + "falling through to generate second code (extreme race)", azureAdId);
        }

        String email = oidcUser.getEmail();
        // Azure AD may not populate standard 'email' claim — fall back to preferred_username or upn
        if (email == null) {
            email = oidcUser.getClaimAsString("preferred_username");
        }
        if (email == null) {
            email = oidcUser.getClaimAsString("upn");
        }
        String displayName = oidcUser.getFullName();
        if (displayName == null) {
            displayName = oidcUser.getClaimAsString("name");
        }
        String title = oidcUser.getClaimAsString("jobTitle");

        Set<String> oidcRoles = new HashSet<>(
                Optional.ofNullable(oidcUser.getClaimAsStringList("roles")).orElse(Collections.emptyList()));
        Set<String> permissions = new HashSet<>();
        Set<String> teams = new HashSet<>(
                Optional.ofNullable(oidcUser.getClaimAsStringList("teams")).orElse(Collections.emptyList()));
        Set<String> groups = new HashSet<>(
                Optional.ofNullable(oidcUser.getClaimAsStringList("groups")).orElse(Collections.emptyList()));

        // Fetch Microsoft Graph profile for enrichment BEFORE role derivation
        // so that `department` is available for the SF role signal logic.
        String department = null;
        String employeeId = null;
        String managerAzureAdId = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

            if (client != null && client.getAccessToken() != null) {
                String graphAccessToken = client.getAccessToken().getTokenValue();

                Optional<GraphUserProfile> profile = graphService.fetchMyProfile(graphAccessToken);
                if (profile.isPresent()) {
                    department = profile.get().department();
                    employeeId = profile.get().employeeId();
                    log.info("Graph enrichment for {} — department={}, employeeId={}",
                            email, department, employeeId);
                }

                Optional<GraphUserProfile> manager = graphService.fetchMyManager(graphAccessToken);
                if (manager.isPresent()) {
                    managerAzureAdId = manager.get().id();
                    log.info("Graph manager for {} — managerAzureAdId={}", email, managerAzureAdId);
                }
            } else {
                log.warn("No OAuth2AuthorizedClient available for Graph enrichment of user {}", email);
            }
        }

        // When Entra provides no roles, derive a starting set from SF SuccessFactors data.
        // The 5-role baseline is always added regardless of Entra roles.
        Set<String> roles;
        if (oidcRoles.isEmpty()) {
            roles = sfRoleDerivationService.deriveRolesFromSfProfile(azureAdId, department, title);
        } else {
            // Entra roles present — still add the 5-role baseline (additive floor).
            roles = new HashSet<>(oidcRoles);
            roles.addAll(List.of("SURVEY_RESPONDENT", "ASSESSMENT_CANDIDATE",
                    "PEER_NOMINATOR", "BROADCAST_VIEWER", "SPARK_VOTER"));
        }

        User user = userProvisioningService.provisionOrUpdateUser(
                azureAdId, email, displayName, title, roles, permissions, teams, groups,
                department, employeeId, managerAzureAdId);

        // Audit every role derivation/grant. Required for defense platform compliance —
        // silent role grants are not acceptable regardless of their source.
        // Action reflects the actual source: SF derivation or Entra-supplied roles.
        String auditAction = oidcRoles.isEmpty() ? "ROLE_DERIVE_SF" : "ROLE_PROVISION_ENTRA";
        auditService.logAction(user.getId(), auditAction, "user", user.getId(),
                auditService.buildDetail("roles", roles.toString()), null);

        // Generate Pulse JWT tokens (NOT Azure tokens)
        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(
                user, request.getHeader("User-Agent"), request.getRemoteAddr());

        log.info("Pulse JWT issued for user {} ({}) after Azure AD login", user.getEmail(), user.getId());

        // Issue a short-lived, single-use opaque code instead of embedding tokens directly
        // in the deep link URL. Tokens in URLs are exposed in browser history, server access
        // logs, and HTTP Referer headers — the code approach avoids all of these leaks.
        //
        // The code is stored in Redis for 60 seconds. The Flutter app immediately exchanges
        // it via POST /api/auth/exchange and the entry is deleted on first read.
        String exchangeCode = UUID.randomUUID().toString().replace("-", "");
        try {
            String jsonValue = objectMapper.writeValueAsString(Map.of("at", accessToken, "rt", refreshToken));
            stringRedisTemplate.opsForValue().set(
                    "oauth_code:" + exchangeCode, jsonValue, cacheTtlProps.oauthCodeTtl());
        } catch (JsonProcessingException e) {
            // Should never happen for simple string maps; rethrow as IO so the method signature is satisfied.
            throw new IOException("Failed to serialize exchange code payload", e);
        }

        // Record this code against the OIDC subject so a duplicate /login/success
        // request within the next 30 s can reuse it instead of creating a new session.
        stringRedisTemplate.opsForValue().set(dedupKey, exchangeCode, cacheTtlProps.oauthDedupTtl());

        log.info("OAuth exchange code issued for user {} ({})", user.getEmail(), user.getId());

        renderDeepLinkHtml(response, exchangeCode);
    }

    // Serves an intermediate HTML page that triggers the Flutter deep link via JavaScript.
    // In-app browsers (iOS Safari, WKWebView) cannot follow a 302 to a custom scheme like
    // pulse://, so we load an HTTP page first and use JS to open the deep link.
    //
    // deepLinkScheme is validated at startup by @PostConstruct to ensure it only
    // contains [a-z][a-z0-9.]* — safe for direct HTML embedding without escaping.
    private void renderDeepLinkHtml(HttpServletResponse response, String exchangeCode) throws IOException {
        String deepLink = deepLinkScheme + "://auth/callback?code=" + exchangeCode;
        response.setContentType("text/html");
        response.getWriter().write("<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>Redirecting to Pulse...</title></head><body>"
                + "<p style='text-align:center;margin-top:40vh;font-family:sans-serif;'>"
                + "Redirecting to Pulse...</p>"
                + "<script>window.location.href=\"" + deepLink.replace("\"", "\\\"") + "\";</script>"
                + "</body></html>");
    }
}
