package com.edge.pulse.configs;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Configuration
@Slf4j
public class RateLimitingConfig implements DisposableBean {

    @org.springframework.beans.factory.annotation.Value("${rate-limiting.enabled:true}")
    private boolean rateLimitingEnabled;

    // General API limit: 100 requests per 60 seconds per user+IP
    private static final int GENERAL_CAPACITY       = 100;
    private static final int GENERAL_REFILL_TOKENS  = 100;
    private static final int GENERAL_REFILL_SECONDS = 60;

    // Auth endpoint limit: 10 requests per 5 minutes per IP.
    // Brute-force protection for /api/auth/* (login, refresh, logout).
    // Keyed by IP only — user is not yet known at auth time.
    private static final int AUTH_CAPACITY       = 10;
    private static final int AUTH_REFILL_TOKENS  = 10;
    private static final int AUTH_REFILL_SECONDS = 300; // 5 minutes

    private static final BucketConfiguration GENERAL_CONFIG = BucketConfiguration.builder()
            .addLimit(limit -> limit
                    .capacity(GENERAL_CAPACITY)
                    .refillIntervally(GENERAL_REFILL_TOKENS, Duration.ofSeconds(GENERAL_REFILL_SECONDS)))
            .build();

    private static final BucketConfiguration AUTH_CONFIG = BucketConfiguration.builder()
            .addLimit(limit -> limit
                    .capacity(AUTH_CAPACITY)
                    .refillIntervally(AUTH_REFILL_TOKENS, Duration.ofSeconds(AUTH_REFILL_SECONDS)))
            .build();

    private final ProxyManager<String> proxyManager;
    private final StatefulRedisConnection<String, byte[]> rateLimitConn;

    public RateLimitingConfig(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisClient redisClient = (RedisClient) lettuceConnectionFactory.getNativeClient();
        this.rateLimitConn = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
        this.proxyManager = LettuceBasedProxyManager.builderFor(rateLimitConn)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(
                                Duration.ofSeconds(AUTH_REFILL_SECONDS)))
                .build();
        log.info("Redis-backed rate limiter initialised");
    }

    @Override
    public void destroy() {
        rateLimitConn.close();
    }

    /**
     * Extracts the JWT {@code sub} claim from a Bearer Authorization header.
     * Returns {@code null} if the header is absent, malformed, or cannot be decoded.
     * Only the payload segment is decoded — no signature verification is needed
     * here because the security filter chain already validates the token.
     */
    private static String extractSubFromJwt(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
        try {
            String token = authHeader.substring(7);
            String[] parts = token.split("\\.", -1);
            if (parts.length < 2) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(
                    parts[1].length() % 4 == 0 ? parts[1] : parts[1] + "=".repeat(4 - parts[1].length() % 4));
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            // Extract "sub":"<value>" without a JSON library dependency in this config class.
            int subIdx = payload.indexOf("\"sub\"");
            if (subIdx < 0) return null;
            int colonIdx = payload.indexOf(':', subIdx + 5);
            if (colonIdx < 0) return null;
            int start = payload.indexOf('"', colonIdx + 1);
            if (start < 0) return null;
            int end = payload.indexOf('"', start + 1);
            if (end < 0) return null;
            return payload.substring(start + 1, end);
        } catch (Exception e) {
            return null;
        }
    }

    @Bean
    @Order(1)
    public Filter rateLimitingFilter() {
        return (request, response, chain) -> {
            if (!rateLimitingEnabled) {
                chain.doFilter(request, response);
                return;
            }
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse res = (HttpServletResponse) response;

            String ip   = req.getRemoteAddr();
            String path = req.getRequestURI();

            ConsumptionProbe generalProbe;
            try {
                // Strict auth bucket applied only to token-issuance endpoints that are
                // actual brute-force targets. /api/auth/me, /api/auth/logout, and the
                // OAuth2 initiation redirect are NOT included — they are safe GET endpoints
                // or already protected by Azure AD's own throttling.
                if (path.equals("/api/auth/refresh") || path.startsWith("/login/oauth2/")) {
                    ConsumptionProbe authProbe = proxyManager.builder()
                            .build("rl:auth:" + ip, () -> AUTH_CONFIG)
                            .tryConsumeAndReturnRemaining(1);
                    if (!authProbe.isConsumed()) {
                        long wait = authProbe.getNanosToWaitForRefill() / 1_000_000_000;
                        res.setHeader("X-Rate-Limit-Remaining", "0");
                        res.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(Math.max(wait, 1)));
                        res.setStatus(429);
                        res.setContentType("text/plain");
                        res.getWriter().write("Too many authentication attempts. Try again in " + wait + " seconds.");
                        return;
                    }
                }

                // General limit applied to every request (including auth after the above check).
                // Prefer the JWT sub claim over the container's RemoteUser, which may be null
                // before the Spring Security filter chain runs for stateless JWT auth.
                String user       = extractSubFromJwt(req.getHeader("Authorization"));
                if (user == null) user = req.getRemoteUser();
                String generalKey = (user != null ? user : "anon") + ":" + ip;
                generalProbe = proxyManager.builder()
                        .build("rl:general:" + generalKey, () -> GENERAL_CONFIG)
                        .tryConsumeAndReturnRemaining(1);

            } catch (Exception e) {
                // Redis unavailable: fail open so the service stays reachable.
                log.warn("Rate limiter Redis unavailable — allowing request: {}", e.getMessage());
                chain.doFilter(request, response);
                return;
            }

            if (generalProbe.isConsumed()) {
                res.setHeader("X-Rate-Limit-Remaining", String.valueOf(generalProbe.getRemainingTokens()));
                chain.doFilter(request, response);
            } else {
                long wait = generalProbe.getNanosToWaitForRefill() / 1_000_000_000;
                res.setHeader("X-Rate-Limit-Remaining", "0");
                res.setHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(Math.max(wait, 1)));
                res.setStatus(429);
                res.setContentType("text/plain");
                res.getWriter().write("Rate limit exceeded. Try again in " + wait + " seconds.");
            }
        };
    }
}
