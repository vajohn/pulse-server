package com.edge.pulse.configs;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DeviceAttestationFilter}.
 *
 * <p>Phase 1: the filter is non-blocking — all requests pass through regardless
 * of whether the {@code X-Device-Attestation} header is present.
 */
class DeviceAttestationFilterTest {

    private DeviceAttestationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DeviceAttestationFilter();
    }

    // ── Pass-through behaviour ───────────────────────────────────────────────

    @Test
    void requestWithAttestationHeader_passesThrough() throws Exception {
        var request  = apiRequest("GET", "/api/spark/home");
        var response = new MockHttpServletResponse();
        var chain    = mock(FilterChain.class);

        request.addHeader(DeviceAttestationFilter.ATTESTATION_HEADER,
                "eyJwbGF0Zm9ybSI6ImFuZHJvaWQifQ");

        filter.doFilter(request, response, chain);

        // Chain must be called — filter never blocks
        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void requestWithoutAttestationHeader_passesThrough() throws Exception {
        var request  = apiRequest("GET", "/api/spark/home");
        var response = new MockHttpServletResponse();
        var chain    = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void requestWithBlankAttestationHeader_passesThrough() throws Exception {
        var request  = apiRequest("POST", "/api/ai/chat");
        var response = new MockHttpServletResponse();
        var chain    = mock(FilterChain.class);

        request.addHeader(DeviceAttestationFilter.ATTESTATION_HEADER, "   ");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
    }

    // ── shouldNotFilter() ────────────────────────────────────────────────────

    @Test
    void authLoginPath_isExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("POST", "/api/auth/login"))).isTrue();
    }

    @Test
    void authRefreshPath_isExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("POST", "/api/auth/refresh"))).isTrue();
    }

    @Test
    void healthPath_isExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("GET", "/api/health"))).isTrue();
    }

    @Test
    void nonApiPath_isExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("GET", "/swagger-ui/index.html"))).isTrue();
    }

    @Test
    void sparkApiPath_isNotExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("GET", "/api/spark/home"))).isFalse();
    }

    @Test
    void aiChatPath_isNotExcluded() {
        assertThat(filter.shouldNotFilter(apiRequest("POST", "/api/ai/chat"))).isFalse();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static MockHttpServletRequest apiRequest(String method, String uri) {
        return new MockHttpServletRequest(method, uri);
    }
}
