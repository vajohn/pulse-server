package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.EngagementSummaryDto;
import com.edge.pulse.services.AnalyticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Authorization test for {@link WebAnalyticsController} (PULSE-WEB-4).
 *
 * <p>Boots a minimal {@code @EnableMethodSecurity} context that proxies the real
 * controller bean, so the {@link PreAuthorize} guard is actually evaluated:
 * a caller WITH {@code REPORT_VIEW} succeeds; a caller WITHOUT it is denied with
 * {@link AccessDeniedException} (which Spring Security maps to HTTP 403). The
 * principal mirrors the codebase convention
 * ({@code UsernamePasswordAuthenticationToken(USER_ID, null, authorities)}).
 */
class WebAnalyticsControllerAuthTest {

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean
        AnalyticsService analyticsService() {
            AnalyticsService svc = mock(AnalyticsService.class);
            when(svc.getEngagementSummary(any(), any(), any(Boolean.class), any(Integer.class), any()))
                    .thenReturn(EngagementSummaryDto.masked("GLOBAL", null, null, true, 30));
            return svc;
        }

        @Bean
        WebAnalyticsController webAnalyticsController(AnalyticsService analyticsService) {
            return new WebAnalyticsController(analyticsService);
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(String... authorities) {
        UUID userId = UUID.randomUUID(); // real-shaped principal (UUID), per codebase convention
        var auths = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, auths));
    }

    @Test
    void withReportView_isAllowed() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WebAnalyticsController controller = ctx.getBean(WebAnalyticsController.class);
            authenticateWith("REPORT_VIEW");

            var response = controller.getEngagement(UUID.randomUUID(), "GLOBAL", null, true, 30);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody()).isNotNull();
        }
    }

    @Test
    void withoutReportView_isDenied() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WebAnalyticsController controller = ctx.getBean(WebAnalyticsController.class);
            authenticateWith("SPARK_NOMINATE"); // an unrelated permission (e.g. EMPLOYEE)

            assertThatThrownBy(() ->
                    controller.getEngagement(UUID.randomUUID(), "GLOBAL", null, true, 30))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void unauthenticated_isDenied() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            WebAnalyticsController controller = ctx.getBean(WebAnalyticsController.class);
            // no authentication set

            assertThatThrownBy(() ->
                    controller.getEngagement(UUID.randomUUID(), "GLOBAL", null, true, 30))
                    .isInstanceOf(Exception.class); // AuthenticationCredentialsNotFound / AccessDenied
        }
    }
}
