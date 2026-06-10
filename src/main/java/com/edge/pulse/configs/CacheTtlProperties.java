package com.edge.pulse.configs;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Centralises all Redis TTL knobs for the Pulse application.
 *
 * <p>Every TTL has a sensible default so the service starts without any environment
 * variables, but each can be tuned for a given deployment:
 *
 * <ul>
 *   <li>{@code ASSIGNMENT_CACHE_TTL_MINUTES} – how long a user's assignment list is
 *       cached. Org-unit assignment mutations do <em>not</em> perform an eager
 *       SCAN-eviction; the TTL is the stale window.
 *   <li>{@code SESSION_CACHE_TTL_MINUTES} – lifetime of the open-session Redis hint
 *       (2 h default matches the longest expected survey session).
 *   <li>{@code ANON_PULSE_ID_TTL_HOURS} – ephemeral anonymous-identity rotation window.
 *   <li>{@code OAUTH_CODE_TTL_SECONDS} – single-use exchange code that the Flutter app
 *       redeems immediately after the deep-link redirect.
 *   <li>{@code OAUTH_DEDUP_TTL_SECONDS} – deduplication lock for duplicate
 *       {@code /login/success} callbacks from Chrome Custom Tab.
 *   <li>{@code PERMISSION_TTL_MINUTES} – per-role permission set cache TTL used by
 *       {@link com.edge.pulse.services.PermissionCacheService}. Default 5 min.
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "pulse.cache")
@Validated
@Getter
@Setter
public class CacheTtlProperties {

    /** User assignment list cache TTL. Default 10 min. Must be ≥ 1 min. */
    @Min(value = 1, message = "pulse.cache.assignment-ttl-minutes must be at least 1")
    private int assignmentTtlMinutes = 10;

    /**
     * Open-session Redis hint TTL. Must comfortably exceed the longest expected
     * psychometric test or survey session. Default 120 min (2 h). Must be ≥ 1 min.
     */
    @Min(value = 1, message = "pulse.cache.session-ttl-minutes must be at least 1")
    private int sessionTtlMinutes = 120;

    /**
     * Ephemeral anonymous-pulse-ID rotation window.
     * After this period a new anonymous ID is generated on the next login.
     * Default 24 h. Must be ≥ 1 h.
     */
    @Min(value = 1, message = "pulse.cache.anon-pulse-id-ttl-hours must be at least 1")
    private int anonPulseIdTtlHours = 24;

    /**
     * Lifetime of the single-use OAuth exchange code stored in Redis.
     * The Flutter app redeems this immediately; 60 s is generous. Default 60 s.
     * Must be ≥ 10 s to allow for slow mobile deep-link redirects.
     */
    @Min(value = 10, message = "pulse.cache.oauth-code-ttl-seconds must be at least 10")
    private int oauthCodeTtlSeconds = 60;

    /**
     * Deduplication lock TTL for duplicate {@code /login/success} callbacks.
     * Must exceed the Chrome Custom Tab retry interval (~100 ms). Default 30 s.
     * Must be ≥ 1 s.
     */
    @Min(value = 1, message = "pulse.cache.oauth-dedup-ttl-seconds must be at least 1")
    private int oauthDedupTtlSeconds = 30;

    /**
     * Per-role permission set cache TTL used by {@link com.edge.pulse.services.PermissionCacheService}.
     * Default 5 min. Must be ≥ 1 min.
     */
    @Min(value = 1, message = "pulse.cache.permission-ttl-minutes must be at least 1")
    private int permissionTtlMinutes = 5;

    // -----------------------------------------------------------------------
    // Convenience Duration accessors
    // -----------------------------------------------------------------------

    public Duration assignmentTtl() {
        return Duration.ofMinutes(assignmentTtlMinutes);
    }

    public Duration sessionTtl() {
        return Duration.ofMinutes(sessionTtlMinutes);
    }

    public Duration anonPulseIdTtl() {
        return Duration.ofHours(anonPulseIdTtlHours);
    }

    public Duration oauthCodeTtl() {
        return Duration.ofSeconds(oauthCodeTtlSeconds);
    }

    public Duration oauthDedupTtl() {
        return Duration.ofSeconds(oauthDedupTtlSeconds);
    }

    public Duration permissionTtl() {
        return Duration.ofMinutes(permissionTtlMinutes);
    }
}
