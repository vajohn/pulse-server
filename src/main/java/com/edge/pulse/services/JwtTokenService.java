package com.edge.pulse.services;

import com.edge.pulse.configs.JwtProperties;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.Session;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.SessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenService {
    private final JwtProperties jwtProperties;
    private final SessionRepository sessionRepository;
    private final StringRedisTemplate redisTemplate;
    private final AnonPulseIdService anonPulseIdService;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).collect(Collectors.toList())
                : Collections.emptyList();

        var builder = Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .claim("anonymous_pulse_id", anonPulseIdService.getOrCreate(user.getId()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey());

        if (user.getOrgUnit() != null) {
            builder.claim("org_unit_id", user.getOrgUnit().getId().toString());
        }

        return builder.compact();
    }

    @Transactional
    public String generateRefreshToken(User user, String deviceInfo, String ipAddress) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        Session session = Session.builder()
                .user(user)
                .refreshTokenHash(tokenHash)
                .deviceInfo(deviceInfo)
                .ipAddress(ipAddress)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpiration() / 1000))
                .build();

        sessionRepository.save(session);
        return rawToken;
    }

    public Claims validateAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (isBlacklisted(token)) {
                throw new JwtException("Token has been revoked");
            }

            return claims;
        } catch (JwtException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public Map<String, String> refreshAccessToken(String refreshToken, User user) {
        String tokenHash = sha256(refreshToken);
        // Pessimistic lock prevents two concurrent requests from both seeing the session
        // as valid and both issuing new tokens (token rotation race condition).
        Optional<Session> sessionOpt = sessionRepository.findForRefreshByHash(tokenHash);

        if (sessionOpt.isEmpty()) {
            throw new JwtException("Invalid or revoked refresh token");
        }

        Session session = sessionOpt.get();
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new JwtException("Refresh token expired");
        }

        // Revoke old refresh token
        session.setRevokedAt(LocalDateTime.now());
        sessionRepository.save(session);

        // Generate new tokens
        String newAccessToken = generateAccessToken(user);
        String newRefreshToken = generateRefreshToken(user, session.getDeviceInfo(), session.getIpAddress());

        return Map.of("access_token", newAccessToken, "refresh_token", newRefreshToken);
    }

    @Transactional
    public void revokeRefreshToken(String refreshToken) {
        String tokenHash = sha256(refreshToken);
        sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(tokenHash)
                .ifPresent(session -> {
                    session.setRevokedAt(LocalDateTime.now());
                    sessionRepository.save(session);
                });
    }

    public void blacklistAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(BLACKLIST_PREFIX + sha256(token), "1", remainingMs, TimeUnit.MILLISECONDS);
            }
        } catch (JwtException e) {
            log.debug("Cannot blacklist invalid token: {}", e.getMessage());
        }
    }

    private boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + sha256(token)));
        } catch (DataAccessException e) {
            log.error("Redis unavailable during blacklist check — failing closed. " +
                      "All requests will be rejected until Redis recovers.", e);
            return true;  // fail-closed: cannot confirm token is not revoked
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
