package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Manages ephemeral anonymous pulse IDs stored in Redis.
 *
 * <p>The TTL is configurable via {@code ANON_PULSE_ID_TTL_HOURS} (default 24 h).
 * The mapping between a real user identity and their anonymous pulse ID is never
 * persisted in the relational database. When the Redis key expires, the mapping is gone
 * and a new ID is generated on the next JWT issuance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnonPulseIdService {

    private final StringRedisTemplate redisTemplate;
    private final CacheTtlProperties cacheTtlProps;

    static final String KEY_PREFIX = "anon:pulse_id:";

    /**
     * Returns the user's current ephemeral anonymous pulse ID, creating it if absent.
     * Uses SETNX semantics to handle concurrent calls safely.
     */
    public String getOrCreate(UUID userId) {
        String key = KEY_PREFIX + userId;
        try {
            String existing = redisTemplate.opsForValue().get(key);
            if (existing != null) return existing;

            String newId = UUID.randomUUID().toString();
            Boolean set = redisTemplate.opsForValue().setIfAbsent(key, newId, cacheTtlProps.anonPulseIdTtl());
            if (Boolean.FALSE.equals(set)) {
                // Another thread won the race — read the winner
                String winner = redisTemplate.opsForValue().get(key);
                return winner != null ? winner : newId;
            }
            return newId;
        } catch (DataAccessException e) {
            // Redis unavailable — generate a transient ID so login is not blocked.
            // This ID will not be cached; the next JWT issuance will retry Redis.
            log.warn("Redis unavailable for anon pulse ID lookup (userId={}) — using transient ID", userId);
            return UUID.randomUUID().toString();
        }
    }
}
