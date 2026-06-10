package com.edge.pulse.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Phase-1 device attestation filter.
 *
 * <p>Reads the {@code X-Device-Attestation} header sent by the Flutter app and
 * logs its presence for audit purposes. Requests without the header are not
 * blocked at this phase — the filter is a passive observer only.
 *
 * <p>Phase 2 (future work): validate the token via the OS trust API
 * (Play Integrity / Apple DCAppAttest) and surface a device-trust level in
 * the {@code SecurityContext} so controllers can adjust behaviour accordingly.
 */
@Component
@Slf4j
public class DeviceAttestationFilter extends OncePerRequestFilter {

    static final String ATTESTATION_HEADER = "X-Device-Attestation";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String attestation = request.getHeader(ATTESTATION_HEADER);
        if (attestation != null && !attestation.isBlank()) {
            log.debug("[DeviceAttestation] header present for {} {} (len={})",
                    request.getMethod(), request.getRequestURI(), attestation.length());
        }
        // Non-blocking: all requests pass through regardless of header presence.
        chain.doFilter(request, response);
    }

    /** Skip public/OAuth paths to keep noise out of the debug log. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/")
                || uri.startsWith("/api/auth/")
                || uri.startsWith("/api/health");
    }
}
