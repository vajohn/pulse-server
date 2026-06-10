package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnonPulseIdServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Spy  private CacheTtlProperties cacheTtlProps = new CacheTtlProperties();

    private AnonPulseIdService service;

    @BeforeEach
    void setUp() {
        service = new AnonPulseIdService(redisTemplate, cacheTtlProps);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void getOrCreate_cacheMiss_generatesAndStoresId() {
        UUID userId = UUID.randomUUID();
        String key = AnonPulseIdService.KEY_PREFIX + userId;

        when(valueOps.get(key)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(key), anyString(), any(Duration.class))).thenReturn(true);

        String result = service.getOrCreate(userId);

        assertThat(result).isNotBlank();
        // must be a valid UUID string
        assertThat(UUID.fromString(result)).isNotNull();
        verify(valueOps).setIfAbsent(eq(key), eq(result), any(Duration.class));
    }

    @Test
    void getOrCreate_cacheHit_returnsCachedId() {
        UUID userId = UUID.randomUUID();
        String cached = UUID.randomUUID().toString();
        String key = AnonPulseIdService.KEY_PREFIX + userId;

        when(valueOps.get(key)).thenReturn(cached);

        String result = service.getOrCreate(userId);

        assertThat(result).isEqualTo(cached);
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void getOrCreate_concurrentRaceLost_readsWinnersValue() {
        UUID userId = UUID.randomUUID();
        String key = AnonPulseIdService.KEY_PREFIX + userId;
        String winnerId = UUID.randomUUID().toString();

        // Initial read: cache miss
        when(valueOps.get(key)).thenReturn(null).thenReturn(winnerId);
        // setIfAbsent returns false — another thread won the race
        when(valueOps.setIfAbsent(eq(key), anyString(), any(Duration.class))).thenReturn(false);

        String result = service.getOrCreate(userId);

        assertThat(result).isEqualTo(winnerId);
    }

    @Test
    void getOrCreate_raceLoser_winnerKeyMissing_returnsGeneratedId() {
        UUID userId = UUID.randomUUID();
        String key = AnonPulseIdService.KEY_PREFIX + userId;

        // Initial read: cache miss; winner key already expired by the time we retry
        when(valueOps.get(key)).thenReturn(null);
        when(valueOps.setIfAbsent(eq(key), anyString(), any(Duration.class))).thenReturn(false);
        // Second get() returns null — key expired between setIfAbsent and retry read

        String result = service.getOrCreate(userId);

        assertThat(result).isNotBlank();
        assertThat(UUID.fromString(result)).isNotNull();
    }

    @Test
    void getOrCreate_redisUnavailable_returnsTransientIdWithoutThrowing() {
        UUID userId = UUID.randomUUID();
        String key = AnonPulseIdService.KEY_PREFIX + userId;

        when(valueOps.get(key)).thenThrow(new DataAccessResourceFailureException("Redis down"));

        String result = service.getOrCreate(userId);

        // Login must not be blocked — a valid transient UUID is returned
        assertThat(result).isNotBlank();
        assertThat(UUID.fromString(result)).isNotNull();
        // No write attempted after the exception
        verify(valueOps, never()).setIfAbsent(any(), any(), any(Duration.class));
    }
}
