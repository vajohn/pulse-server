package com.edge.pulse.services.translation;

import com.edge.pulse.data.enums.TranslationProvider;

import java.util.List;

/**
 * No-op {@link TranslationService} used when no provider is configured
 * ({@code provider = NONE}) or as a placeholder for not-yet-implemented providers.
 *
 * <p>Returns every input text unchanged. {@link #isAvailable()} returns {@code false}
 * so callers can optionally detect the degraded state (e.g. to show a UI warning).
 */
public class NullTranslationService implements TranslationService {

    @Override
    public String translate(String text, String fromLocale, String toLocale) {
        return text;
    }

    @Override
    public List<String> translateBatch(List<String> texts, String fromLocale, String toLocale) {
        return texts;
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public TranslationProvider getProvider() {
        return TranslationProvider.NONE;
    }
}
