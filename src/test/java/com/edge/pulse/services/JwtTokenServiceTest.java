package com.edge.pulse.services;

import com.edge.pulse.configs.JwtProperties;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.Session;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.SessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private AnonPulseIdService anonPulseIdService;

    private JwtTokenService jwtTokenService;
    private User testUser;
    private String testAnonPulseId;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("pulse-jwt-secret-key-change-in-production-minimum-256-bits-long-enough");
        props.setAccessTokenExpiration(900000);
        props.setRefreshTokenExpiration(604800000);

        jwtTokenService = new JwtTokenService(props, sessionRepository, redisTemplate, anonPulseIdService);

        testAnonPulseId = UUID.randomUUID().toString();
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@edge.com")
                .roles(Set.of(Role.builder().name("EMPLOYEE").build()))
                .build();
        lenient().when(anonPulseIdService.getOrCreate(testUser.getId())).thenReturn(testAnonPulseId);
    }

    @Test
    void generateAccessToken_producesValidToken() {
        String token = jwtTokenService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        Claims claims = jwtTokenService.validateAccessToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(testUser.getId().toString());
        assertThat(claims.get("email")).isEqualTo("test@edge.com");
        assertThat(claims.get("anonymous_pulse_id")).isEqualTo(testAnonPulseId);
    }

    @Test
    void validateAccessToken_returnsNullForInvalidToken() {
        Claims claims = jwtTokenService.validateAccessToken("invalid.token.here");
        assertThat(claims).isNull();
    }

    @Test
    void validateAccessToken_returnsNullForBlacklistedToken() {
        String token = jwtTokenService.generateAccessToken(testUser);

        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        Claims claims = jwtTokenService.validateAccessToken(token);
        assertThat(claims).isNull();
    }

    @Test
    void validateAccessToken_failsClosedWhenRedisUnavailable() {
        String token = jwtTokenService.generateAccessToken(testUser);
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Redis down"));

        Claims result = jwtTokenService.validateAccessToken(token);

        assertThat(result).isNull();
    }

    @Test
    void generateRefreshToken_createsSession() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        String refreshToken = jwtTokenService.generateRefreshToken(testUser, "TestDevice", "127.0.0.1");

        assertThat(refreshToken).isNotBlank();
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void refreshAccessToken_rotatesTokens() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));
        String refreshToken = jwtTokenService.generateRefreshToken(testUser, "TestDevice", "127.0.0.1");

        Session session = Session.builder()
                .user(testUser)
                .deviceInfo("TestDevice")
                .ipAddress("127.0.0.1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        // Mock finding the session by hash (pessimistic lock variant used by refreshAccessToken)
        when(sessionRepository.findForRefreshByHash(anyString()))
                .thenReturn(Optional.of(session));

        var tokens = jwtTokenService.refreshAccessToken(refreshToken, testUser);

        assertThat(tokens).containsKeys("access_token", "refresh_token");
        assertThat(tokens.get("access_token")).isNotBlank();
        assertThat(tokens.get("refresh_token")).isNotBlank();
    }

    @Test
    void blacklistAccessToken_addsToRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String token = jwtTokenService.generateAccessToken(testUser);
        jwtTokenService.blacklistAccessToken(token);

        verify(valueOperations).set(anyString(), eq("1"), anyLong(), any());
    }

    @Test
    void revokeRefreshToken_setsRevokedAt() {
        Session session = Session.builder()
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(sessionRepository.findByRefreshTokenHashAndRevokedAtIsNull(anyString()))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        jwtTokenService.revokeRefreshToken("some-refresh-token");

        assertThat(session.getRevokedAt()).isNotNull();
        verify(sessionRepository).save(session);
    }
}
