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
 * <p>User resolution on {@code /complete} is governed by {@link com.edge.pulse.configs.X4AuthProperties#getMatchMode()}:
 * <ul>
 *   <li><b>EMPLOYEE_NUMBER_STRICT</b> — matches the {@code x4auth:employeeId} claim against a
 *       saf-synced Pulse employee ({@code findByEmployeeId} then {@code findBySfUserId} fallback);
 *       rejects the login with 403 if no match. No bare auto-provisioning in this mode.</li>
 *   <li><b>EMAIL</b> (default, transitional) — matches by email and auto-provisions a bare
 *       {@code EMPLOYEE} if absent; deployed until X4Auth reliably emits the employeeId claim,
 *       then k2 gitops flips matchMode to STRICT.</li>
 * </ul>
 * RBAC remains Pulse-authoritative in both modes ({@code x4auth:roles} are logged, not applied).
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
    private final com.edge.pulse.configs.X4AuthProperties x4AuthProperties;

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
        User user;
        if ("EMPLOYEE_NUMBER_STRICT".equalsIgnoreCase(x4AuthProperties.getMatchMode())) {
            String employeeId = approved.employeeId();
            if (employeeId == null || employeeId.isBlank()) {
                log.warn("X4Auth STRICT login rejected: no employeeId claim for {}", email);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "NO_EMPLOYEE_RECORD",
                                "message", "No employee record found for this account — contact IT."));
            }
            User matched = userRepository.findByEmployeeId(employeeId)
                    .or(() -> userRepository.findBySfUserId(employeeId))
                    .orElse(null);
            if (matched == null) {
                log.warn("X4Auth STRICT login rejected: no saf employee for employeeId={}", employeeId);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "NO_EMPLOYEE_RECORD",
                                "message", "No employee record found for this account — contact IT."));
            }
            user = touchLogin(matched);
        } else {
            // Legacy EMAIL mode (transitional, until X4Auth emits x4auth:employeeId).
            user = userRepository.findByEmail(email)
                    .map(this::touchLogin)
                    .orElseGet(() -> autoProvision(email, approved, http.getRemoteAddr()));
        }

        // TODO(tech-debt): Future — Pulse should verify the Tasdiq (X4Auth) token directly on each
        // request and stop minting a separate Pulse JWT here, collapsing to a single token / pure
        // resource-server model. Tracked as tech debt; see the 2026-06-12 identity-hardening spec.
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
