package com.edge.pulse.services.translation;

import com.edge.pulse.data.enums.TranslationProvider;

import java.util.List;

/**
 * Provider-agnostic translation service contract.
 *
 * <p>Implementations are selected at startup by
 * {@link com.edge.pulse.configs.TranslationConfig} based on
 * {@link com.edge.pulse.configs.TranslationProperties#getProvider()}.
 * Callers never depend on a concrete implementation — only this interface.
 *
 * <p>All implementations must be fail-safe: on any provider error the original
 * text(s) are returned unchanged so that translation failure never blocks a save.
 */
public interface TranslationService {

    /**
     * Translate a single text from {@code fromLocale} to {@code toLocale}.
     *
     * @param text       source text (must not be blank)
     * @param fromLocale BCP-47 language code of the source text (e.g. {@code "en"})
     * @param toLocale   BCP-47 language code of the target language (e.g. {@code "ar"})
     * @return translated text, or the original {@code text} if the provider is unavailable
     */
    String translate(String text, String fromLocale, String toLocale);

    /**
     * Translate multiple texts in a single provider call (up to 50 items).
     *
     * <p>The result list is parallel to the input list — index N in the result
     * corresponds to index N in {@code texts}. On provider failure, the original
     * text at each position is returned.
     *
     * @param texts      source texts (non-empty list, max 50 items)
     * @param fromLocale BCP-47 source language code
     * @param toLocale   BCP-47 target language code
     * @return list of translated texts, same size as input
     */
    List<String> translateBatch(List<String> texts, String fromLocale, String toLocale);

    /**
     * Returns {@code true} if the underlying provider is configured and capable
     * of making translation calls.
     */
    boolean isAvailable();

    /** The provider identifier — included in API responses as metadata. */
    TranslationProvider getProvider();
}
