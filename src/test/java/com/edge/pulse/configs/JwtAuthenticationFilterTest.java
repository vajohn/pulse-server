package com.edge.pulse.configs;

import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.PermissionCacheService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenService jwtTokenService;
    @Mock private PermissionCacheService permissionCacheService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenService, permissionCacheService);
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_setsSecurityContext() throws Exception {
        String token = "valid.jwt.token";
        UUID userId = UUID.randomUUID();

        Claims claims = Jwts.claims()
                .subject(userId.toString())
                .add("roles", List.of("EMPLOYEE"))
                .build();

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getRequestURI()).thenReturn("/api/surveys/active");
        when(jwtTokenService.validateAccessToken(token)).thenReturn(claims);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userId);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotSetContext() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid.token");
        when(request.getRequestURI()).thenReturn("/api/surveys/active");
        when(jwtTokenService.validateAccessToken("invalid.token")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void noAuthHeader_passesThrough() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/surveys/active");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void oauthPath_shouldNotFilter() {
        when(request.getRequestURI()).thenReturn("/oauth2/authorization/azure");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void apiPath_shouldFilter() {
        when(request.getRequestURI()).thenReturn("/api/surveys/active");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
