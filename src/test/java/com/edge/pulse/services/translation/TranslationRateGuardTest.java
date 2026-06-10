package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationRateGuardTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private TranslationRateGuard guard;
    private TranslationProperties props;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        props = new TranslationProperties();
        props.setDailyCharBudget(1000);
        when(redis.opsForValue()).thenReturn(valueOps);
        guard = new TranslationRateGuard(redis, props);
    }

    // ── Under budget ──────────────────────────────────────────────────────────

    @Test
    void tryConsume_underBudget_returnsTrue() {
        when(valueOps.increment(anyString(), eq(100L))).thenReturn(100L);

        assertThat(guard.tryConsume(userId, 100)).isTrue();
    }

    @Test
    void tryConsume_atExactBudget_returnsTrue() {
        when(valueOps.increment(anyString(), eq(1000L))).thenReturn(1000L);

        assertThat(guard.tryConsume(userId, 1000)).isTrue();
    }

    // ── Over budget ───────────────────────────────────────────────────────────

    @Test
    void tryConsume_overBudget_returnsFalse() {
        when(valueOps.increment(anyString(), eq(100L))).thenReturn(1001L);

        assertThat(guard.tryConsume(userId, 100)).isFalse();
    }

    // ── TTL set on first increment ────────────────────────────────────────────

    @Test
    void tryConsume_firstIncrement_setsTtl() {
        when(valueOps.increment(anyString(), anyLong())).thenReturn(50L); // first call = value equals charCount

        guard.tryConsume(userId, 50);

        verify(redis).expire(anyString(), eq(Duration.ofHours(25)));
    }

    @Test
    void tryConsume_subsequentIncrement_doesNotResetTtl() {
        when(valueOps.increment(anyString(), anyLong())).thenReturn(200L); // not first increment

        guard.tryConsume(userId, 50);

        verify(redis, never()).expire(any(), any());
    }

    // ── Redis failure → fail-open ─────────────────────────────────────────────

    @Test
    void tryConsume_redisFailure_failsOpenReturnsTrue() {
        when(valueOps.increment(anyString(), anyLong())).thenThrow(new RuntimeException("Redis down"));

        boolean result = guard.tryConsume(userId, 100);

        assertThat(result).isTrue(); // fail-open: cost-control, not a security gate
    }
}
