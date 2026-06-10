package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AuthResponse;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.RoleRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.PermissionCacheService;
import com.edge.pulse.services.X4AuthService;
import com.edge.pulse.services.X4AuthService.InitiateResult;
import com.edge.pulse.services.X4AuthService.PollResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * X4Auth (Tasdiq) push-authentication endpoints — the air-gapped k2 replacement for Azure/Entra login.
 *
 * <p>Additive and self-contained: this controller does not touch the existing Azure OAuth2 flow, so
 * X4Auth can be validated in parallel before Azure is removed. All routes are pre-auth (permitAll) like
 * the existing login endpoints. The client→server contract mirrors the proven x4mahara integration:
 * {@code config → initiate → status/{txn} → complete}.
 *
 * <p>Per the air-gap design decision, an approved login with no matching Pulse user is
 * <b>auto-provisioned</b> as {@code EMPLOYEE}; RBAC remains Pulse-authoritative ({@code x4auth:roles}
 * are logged, not applied). Existing users are matched by email.
 */
@RestController
@RequestMapping("/api/auth/x4auth")
@RequiredArgsConstructor
@Slf4j
public class X4AuthController {

    private static final String DEFAULT_ROLE = "EMPLOYEE";

    private final X4AuthService x4AuthService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenService jwtTokenService;
    private final PermissionCacheService permissionCacheService;
    private final AuditService auditService;

    public record InitiateRequest(@NotBlank @Email String email) {}
    public record CompleteRequest(@NotBlank String transactionId) {}

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("enabled", x4AuthService.isConfigured());
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody InitiateRequest request) {
        if (!x4AuthService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "X4AUTH_NOT_CONFIGURED"));
        }
        InitiateResult r = x4AuthService.initiate(request.email().trim().toLowerCase());
        if (!r.success()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", r.errorCode(),
                    "message", r.errorMessage()));
        }
        // verificationCode may be null (older IdP builds) — omit rather than send null.
        return ResponseEntity.ok(buildInitiateBody(r));
    }

    @GetMapping("/status/{transactionId}")
    public ResponseEntity<?> status(@PathVariable String transactionId) {
        PollResult p = x4AuthService.poll(transactionId);
        return ResponseEntity.ok(Map.of(
                "status", p.status(),
                "isApproved", p.approved()));
    }

    @PostMapping("/complete")
    @Transactional
    public ResponseEntity<?> complete(@Valid @RequestBody CompleteRequest request, HttpServletRequest http) {
        if (!x4AuthService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "X4AUTH_NOT_CONFIGURED"));
        }
        PollResult approved = x4AuthService.consumeApproved(request.transactionId());
        if (approved == null || !approved.approved() || approved.email() == null || approved.email().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "NOT_APPROVED"));
        }

        String email = approved.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .map(existing -> touchLogin(existing))
                .orElseGet(() -> autoProvision(email, approved, http.getRemoteAddr()));

        String accessToken = jwtTokenService.generateAccessToken(user);
        String refreshToken = jwtTokenService.generateRefreshToken(
                user, http.getHeader("User-Agent"), http.getRemoteAddr());

        auditService.logAction(user.getId(), "LOGIN_X4AUTH", "session", null,
                auditService.buildDetail("method", "x4auth", "email", email), http.getRemoteAddr());

        return ResponseEntity.ok(new AuthResponse(
                accessToken, refreshToken, permissionCacheService.toUserSummary(user)));
    }

    private User touchLogin(User user) {
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    /**
     * Air-gap auto-provisioning: create a Pulse user from the verified X4Auth id_token claims with the
     * default EMPLOYEE role. RBAC stays Pulse-authoritative — x4auth:roles are logged, not applied.
     */
    private User autoProvision(String email, PollResult identity, String ip) {
        Role employee = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role '" + DEFAULT_ROLE + "' missing — run seed_rbac_bootstrap.sql"));
        Set<Role> roles = new HashSet<>();
        roles.add(employee);

        User created = userRepository.save(User.builder()
                .email(email)
                .displayName(identity.displayName() != null ? identity.displayName() : email)
                .department(identity.department())
                .roles(roles)
                .active(true)
                .lastLoginAt(LocalDateTime.now())
                .build());

        auditService.logAction(created.getId(), "USER_AUTOPROVISION_X4AUTH", "user", created.getId(),
                auditService.buildDetail("email", email, "role", DEFAULT_ROLE), ip);
        log.info("Auto-provisioned X4Auth user {} as {}", email, DEFAULT_ROLE);
        return created;
    }

    private Map<String, Object> buildInitiateBody(InitiateResult r) {
        if (r.verificationCode() != null) {
            return Map.of(
                    "transactionId", r.transactionId(),
                    "pollInterval", r.pollIntervalMs() != null ? r.pollIntervalMs() : 2000,
                    "verificationCode", r.verificationCode());
        }
        return Map.of(
                "transactionId", r.transactionId(),
                "pollInterval", r.pollIntervalMs() != null ? r.pollIntervalMs() : 2000);
    }
}
