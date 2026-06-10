package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AuthResponse;
import com.edge.pulse.data.dto.RefreshRequest;
import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.Session;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.SessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.PermissionCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AuditService auditService;
    private final PermissionCacheService permissionCacheService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        String refreshToken = request.refreshToken();
        String tokenHash = sha256(refreshToken);

        Optional<Session> sessionOpt = sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(tokenHash);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        User user = sessionOpt.get().getUser();
        Map<String, String> tokens = jwtTokenService.refreshAccessToken(refreshToken, user);
        UserSummary summary = permissionCacheService.toUserSummary(user);

        return ResponseEntity.ok(new AuthResponse(tokens.get("access_token"), tokens.get("refresh_token"), summary));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request) {
        if (authentication == null) {
            return ResponseEntity.ok().build();
        }

        UUID userId = (UUID) authentication.getPrincipal();
        String token = (String) authentication.getCredentials();

        jwtTokenService.blacklistAccessToken(token);

        auditService.logAction(userId, "LOGOUT", "session", null, null, request.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserSummary> me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return ResponseEntity.ok(permissionCacheService.toUserSummary(user));
    }

    /**
     * Exchanges a short-lived, single-use opaque code (issued by the OAuth2 login
     * success handler and stored in Redis) for Pulse access and refresh tokens.
     *
     * <p>The code is consumed atomically on first use (Redis {@code getAndDelete})
     * and expires after 60 seconds, so tokens are never exposed in the deep link
     * URL, server access logs, or browser history.
     */
    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchange(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Atomic single-use consumption: retrieve and immediately delete the code.
        String jsonValue = stringRedisTemplate.opsForValue().getAndDelete("oauth_code:" + code);
        if (jsonValue == null) {
            // Code not found or already consumed — do not indicate which.
            log.warn("OAuth exchange attempted with invalid or expired code");
            return ResponseEntity.status(401).build();
        }

        try {
            Map<String, String> tokens = objectMapper.readValue(jsonValue, new TypeReference<>() {});
            String accessToken  = tokens.get("at");
            String refreshToken = tokens.get("rt");
            if (accessToken == null || refreshToken == null) {
                log.error("OAuth exchange code payload missing token fields");
                return ResponseEntity.status(500).build();
            }
            return ResponseEntity.ok(new AuthResponse(accessToken, refreshToken, null));
        } catch (Exception e) {
            log.error("Failed to deserialize OAuth exchange code payload", e);
            return ResponseEntity.status(500).build();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
