package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Per-user daily character budget guard for the translation endpoints.
 *
 * <p>Tracks cumulative character count per user per UTC calendar day in Redis.
 * Key: {@code translation:budget:{userId}:{yyyy-MM-dd}}.
 * TTL: 25 hours (slightly longer than one day to avoid boundary gaps around midnight UTC).
 *
 * <p>Budget is a cost-control mechanism, not a security boundary.
 * On Redis failure this guard fails open (allows the request) and logs a WARN.
 * An attacker who can bypass Redis still faces the Bucket4j per-request rate limit.
 *
 * @see TranslationProperties#getDailyCharBudget()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranslationRateGuard {

    private static final String KEY_PREFIX = "translation:budget:";
    private static final Duration TTL = Duration.ofHours(25);

    private final StringRedisTemplate redis;
    private final TranslationProperties props;

    /**
     * Attempt to consume {@code charCount} characters from the user's daily budget.
     *
     * @param userId    the user consuming the budget
     * @param charCount number of characters to consume
     * @return {@code true} if the budget allows the request; {@code false} if the daily
     *         limit has been exceeded (caller should return HTTP 429)
     */
    public boolean tryConsume(UUID userId, int charCount) {
        String key = KEY_PREFIX + userId + ":" + LocalDate.now(ZoneOffset.UTC);
        try {
            Long total = redis.opsForValue().increment(key, charCount);
            if (total != null && total == charCount) {
                // First increment for this key — set the TTL
                redis.expire(key, TTL);
            }
            return total == null || total <= props.getDailyCharBudget();
        } catch (Exception e) {
            log.warn("Redis unavailable for translation rate guard — failing open for user {}: {}",
                    userId, e.getMessage());
            return true; // fail-open: cost-control, not a security gate
        }
    }
}
