package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingTranslationServiceTest {

    @Mock private TranslationService delegate;
    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;

    private CachingTranslationService service;
    private TranslationProperties props;

    @BeforeEach
    void setUp() {
        props = new TranslationProperties();
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new CachingTranslationService(delegate, redis, props);
    }

    // ── Cache hit ──────────────────────────────────────────────────────────────

    @Test
    void translateBatch_allCacheHits_skipsDelegate() {
        when(valueOps.get(anyString())).thenReturn("مرحبا");

        List<String> result = service.translateBatch(List.of("Hello"), "en", "ar");

        assertThat(result).containsExactly("مرحبا");
        verify(delegate, never()).translateBatch(any(), any(), any());
    }

    @Test
    void translate_cacheHit_skipsDelegate() {
        when(valueOps.get(anyString())).thenReturn("مرحبا");

        String result = service.translate("Hello", "en", "ar");

        assertThat(result).isEqualTo("مرحبا");
        verifyNoInteractions(delegate);
    }

    // ── Cache miss → delegate → populate cache ────────────────────────────────

    @Test
    void translateBatch_cacheMiss_callsDelegateAndPopulatesCache() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(delegate.translateBatch(List.of("Hello"), "en", "ar")).thenReturn(List.of("مرحبا"));

        List<String> result = service.translateBatch(List.of("Hello"), "en", "ar");

        assertThat(result).containsExactly("مرحبا");
        verify(delegate).translateBatch(List.of("Hello"), "en", "ar");
        verify(valueOps).set(anyString(), eq("مرحبا"), eq(Duration.ofDays(7)));
    }

    // ── Partial batch: some cached, some not ──────────────────────────────────

    @Test
    void translateBatch_partialHit_onlyFetchesMissesFromDelegate() {
        // First text cached, second is a miss
        when(valueOps.get(anyString()))
                .thenReturn("مرحبا")   // first call: Hello cached
                .thenReturn(null);     // second call: Goodbye is a miss
        when(delegate.translateBatch(List.of("Goodbye"), "en", "ar"))
                .thenReturn(List.of("مع السلامة"));

        List<String> result = service.translateBatch(List.of("Hello", "Goodbye"), "en", "ar");

        assertThat(result).containsExactly("مرحبا", "مع السلامة");
        // Only the miss was sent to the delegate
        verify(delegate).translateBatch(List.of("Goodbye"), "en", "ar");
    }

    // ── Redis failure → delegates without caching ─────────────────────────────

    @Test
    void translateBatch_redisFailure_delegatesWithoutCrash() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis down"));
        when(delegate.translateBatch(List.of("Hello"), "en", "ar")).thenReturn(List.of("مرحبا"));

        List<String> result = service.translateBatch(List.of("Hello"), "en", "ar");

        assertThat(result).containsExactly("مرحبا");
        verify(delegate).translateBatch(any(), any(), any());
    }

    // ── translateSingle: cache hit / miss ─────────────────────────────────────

    @Test
    void translateSingle_cacheHit_returnsCachedTrue() {
        when(valueOps.get(anyString())).thenReturn("مرحبا");

        CachingTranslationService.TranslationResult result =
                service.translateSingle("Hello", "en", "ar");

        assertThat(result.text()).isEqualTo("مرحبا");
        assertThat(result.cached()).isTrue();
        verifyNoInteractions(delegate);
    }

    @Test
    void translateSingle_cacheMiss_returnsCachedFalse() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(delegate.translate("Hello", "en", "ar")).thenReturn("مرحبا");

        CachingTranslationService.TranslationResult result =
                service.translateSingle("Hello", "en", "ar");

        assertThat(result.text()).isEqualTo("مرحبا");
        assertThat(result.cached()).isFalse();
        verify(delegate).translate("Hello", "en", "ar");
        verify(valueOps).set(anyString(), eq("مرحبا"), eq(Duration.ofDays(7)));
    }

    // ── isAvailable / getProvider delegates to underlying service ────────────

    @Test
    void isAvailable_delegatesToInner() {
        when(delegate.isAvailable()).thenReturn(true);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void getProvider_delegatesToInner() {
        when(delegate.getProvider()).thenReturn(TranslationProvider.AZURE);
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.AZURE);
    }
}
