package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis-backed caching decorator for any {@link TranslationService}.
 *
 * <p>Wraps a real provider implementation and caches each translated text
 * independently. Key: {@code translation:{from}:{to}:{sha256hex(text)}}.
 * TTL: {@link TranslationProperties#getCacheTtlDays()} days (default 7 days).
 *
 * <p>Per-text caching means the same phrase across different forms hits the cache,
 * maximising reuse without storing entire question sets as single cache entries.
 *
 * <p>Partial-batch logic: cache is checked per text first; only misses are sent to
 * the underlying provider in a single batch call.
 *
 * <p>On Redis failure: delegates to the real service without caching (fail-open),
 * logged at WARN. Translation availability must not depend on Redis availability.
 */
@Slf4j
public class CachingTranslationService implements TranslationService {

    private static final String KEY_PREFIX = "translation:";

    private final TranslationService delegate;
    private final StringRedisTemplate redis;
    private final TranslationProperties props;

    public CachingTranslationService(TranslationService delegate,
                                     StringRedisTemplate redis,
                                     TranslationProperties props) {
        this.delegate = delegate;
        this.redis = redis;
        this.props = props;
    }

    @Override
    public String translate(String text, String fromLocale, String toLocale) {
        List<String> results = translateBatch(List.of(text), fromLocale, toLocale);
        return results.isEmpty() ? text : results.get(0);
    }

    @Override
    public List<String> translateBatch(List<String> texts, String fromLocale, String toLocale) {
        List<String> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        List<Integer> missIndices = new ArrayList<>();
        List<String> missTexts = new ArrayList<>();

        // 1. Check cache for each text
        for (int i = 0; i < texts.size(); i++) {
            String cached = getFromCache(texts.get(i), fromLocale, toLocale);
            if (cached != null) {
                result.set(i, cached);
            } else {
                missIndices.add(i);
                missTexts.add(texts.get(i));
            }
        }

        // 2. Batch-fetch misses from the provider in one call
        if (!missTexts.isEmpty()) {
            List<String> translated = delegate.translateBatch(missTexts, fromLocale, toLocale);
            for (int j = 0; j < missIndices.size(); j++) {
                String translatedText = translated.get(j);
                result.set(missIndices.get(j), translatedText);
                putToCache(missTexts.get(j), fromLocale, toLocale, translatedText);
            }
        }

        // Guarantee no nulls — fall back to original if somehow still null
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) == null) {
                result.set(i, texts.get(i));
            }
        }
        return result;
    }

    /**
     * Translate a single text and report whether the result was served from Redis.
     * Used by {@link com.edge.pulse.controllers.AdminTranslationController} to populate
     * the {@code cached} field in the single-translate API response.
     *
     * <p>Separated from {@link #translate} to avoid threading cache metadata through the
     * {@link TranslationService} interface, which would leak a caching concern into every
     * implementation and test.
     */
    public TranslationResult translateSingle(String text, String fromLocale, String toLocale) {
        String fromCache = getFromCache(text, fromLocale, toLocale);
        if (fromCache != null) {
            return new TranslationResult(fromCache, true);
        }
        String translated = delegate.translate(text, fromLocale, toLocale);
        putToCache(text, fromLocale, toLocale, translated);
        return new TranslationResult(translated, false);
    }

    /** Carries a single translated text and whether it was served from the Redis cache. */
    public record TranslationResult(String text, boolean cached) {}

    @Override
    public boolean isAvailable() {
        return delegate.isAvailable();
    }

    @Override
    public TranslationProvider getProvider() {
        return delegate.getProvider();
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private String cacheKey(String text, String from, String to) {
        return KEY_PREFIX + from + ":" + to + ":" + sha256hex(text);
    }

    private String getFromCache(String text, String from, String to) {
        try {
            return redis.opsForValue().get(cacheKey(text, from, to));
        } catch (Exception e) {
            log.warn("Redis cache read failed for translation key — proceeding without cache: {}",
                    e.getMessage());
            return null;
        }
    }

    private void putToCache(String text, String from, String to, String translated) {
        try {
            redis.opsForValue().set(cacheKey(text, from, to), translated, props.cacheTtl());
        } catch (Exception e) {
            log.warn("Redis cache write failed for translation — result will not be cached: {}",
                    e.getMessage());
        }
    }

    private static String sha256hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present in every JVM
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
