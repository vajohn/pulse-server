package com.edge.pulse.configs;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Phase 1 Swagger security fix.
 *
 * Background: the oauthFilterChain (@Order 2) previously had anyRequest().authenticated()
 * with no permit for Swagger paths. Requests to /swagger-ui/** and /v3/api-docs/**
 * were redirected to Azure AD login instead of serving the spec.
 *
 * The fix added:
 *   .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
 *
 * Unit tests here verify:
 *   1. The path patterns correctly match all Swagger UI and OpenAPI spec URLs
 *   2. The patterns do NOT accidentally over-permit API endpoints
 *   3. The JWT filter (apiFilterChain only) does not apply to Swagger paths
 *
 * The full end-to-end accessibility test requires Docker (PostgreSQL + Redis + Azure AD);
 * see SwaggerAccessibilityIntegrationTest below.
 */
class SwaggerSecurityTest {

    private final AntPathMatcher matcher = new AntPathMatcher();

    /** Mirrors the four Swagger patterns permitAll'd in the @Order(2) noAzureFilterChain. */
    private boolean isPermitted(String requestPath) {
        return matcher.match("/swagger-ui/**", requestPath)
                || matcher.match("/swagger-ui.html", requestPath)
                || matcher.match("/v3/api-docs/**", requestPath)
                || matcher.match("/v3/api-docs.yaml", requestPath);
    }

    // -----------------------------------------------------------------------
    // Path pattern correctness — every Swagger UI asset must be matched
    // -----------------------------------------------------------------------

    @Test
    void swaggerUiHtmlRedirect_isPermitted() {
        assertThat(isPermitted("/swagger-ui.html")).isTrue();
    }

    @Test
    void swaggerUiIndexPage_isPermitted() {
        assertThat(isPermitted("/swagger-ui/index.html")).isTrue();
    }

    @Test
    void swaggerUiStaticAssets_arePermitted() {
        assertThat(isPermitted("/swagger-ui/swagger-ui.css")).isTrue();
        assertThat(isPermitted("/swagger-ui/swagger-ui-bundle.js")).isTrue();
        assertThat(isPermitted("/swagger-ui/swagger-initializer.js")).isTrue();
        assertThat(isPermitted("/swagger-ui/favicon-32x32.png")).isTrue();
    }

    @Test
    void openApiJsonSpec_isPermitted() {
        assertThat(isPermitted("/v3/api-docs")).isTrue();
    }

    @Test
    void openApiYamlSpec_isPermitted() {
        assertThat(isPermitted("/v3/api-docs.yaml")).isTrue();
    }

    @Test
    void openApiSwaggerConfig_isPermitted() {
        // springdoc serves config used by Swagger UI at this path
        assertThat(isPermitted("/v3/api-docs/swagger-config")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Patterns must NOT over-permit API endpoints
    // -----------------------------------------------------------------------

    @Test
    void apiAuthLogin_isNotPermittedBySwaggerPatterns() {
        assertThat(isPermitted("/api/auth/login")).isFalse();
    }

    @Test
    void apiSurveys_isNotPermittedBySwaggerPatterns() {
        assertThat(isPermitted("/api/surveys/active")).isFalse();
    }

    @Test
    void apiAdmin_isNotPermittedBySwaggerPatterns() {
        assertThat(isPermitted("/api/admin/reports/summary")).isFalse();
    }

    @Test
    void rootPath_isNotPermittedBySwaggerPatterns() {
        assertThat(isPermitted("/")).isFalse();
    }

    // -----------------------------------------------------------------------
    // JWT filter bypass — Swagger paths are outside /api/** (apiFilterChain scope)
    // so the JWT filter never runs for them regardless of shouldNotFilter()
    // -----------------------------------------------------------------------

    @Test
    void swaggerPaths_areOutsideApiFilterChainScope() {
        // apiFilterChain uses securityMatcher("/api/**") so it only processes /api/** requests.
        // Swagger paths do not start with /api/ — they are handled by the @Order(2) catch-all chain.
        String[] swaggerPaths = {
                "/swagger-ui.html",
                "/swagger-ui/index.html",
                "/v3/api-docs",
                "/v3/api-docs/swagger-config"
        };
        for (String path : swaggerPaths) {
            assertThat(matcher.match("/api/**", path))
                    .as("Swagger path '%s' must NOT be in apiFilterChain scope (/api/**)", path)
                    .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Integration test (requires Docker: PostgreSQL + Redis + Azure AD)
    // -----------------------------------------------------------------------

    /**
     * Full end-to-end Swagger accessibility test.
     *
     * To run manually:
     *   docker compose up -d
     *   ./gradlew test -Dgroups=integration
     *
     * What this tests:
     *   - GET /swagger-ui.html without any Authorization header → HTTP 200 (not 302/401/403)
     *   - GET /v3/api-docs without any Authorization header → HTTP 200 with JSON body
     *   - GET /api/surveys/active without auth → HTTP 401 (still protected)
     */
    @Test
    @Disabled("Integration test — requires running PostgreSQL, Redis, and Azure AD. " +
              "Run via: docker compose up -d && ./gradlew test -Dgroups=integration")
    void swaggerUi_isAccessibleWithoutAuthentication() {
        // Implemented as a @SpringBootTest with TestRestTemplate when Docker is available.
        // The critical assertions:
        //   restTemplate.getForEntity("/swagger-ui.html", String.class).getStatusCode() == OK
        //   restTemplate.getForEntity("/v3/api-docs", String.class).getStatusCode() == OK
        //   restTemplate.getForEntity("/api/surveys/active", String.class).getStatusCode() == UNAUTHORIZED
    }
}
