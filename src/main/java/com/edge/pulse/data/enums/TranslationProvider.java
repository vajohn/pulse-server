package com.edge.pulse.data.enums;

/**
 * Identifies the active translation backend.
 *
 * <p>Used by {@link com.edge.pulse.configs.TranslationProperties} and
 * {@link com.edge.pulse.configs.TranslationConfig} to select the appropriate
 * {@link com.edge.pulse.services.translation.TranslationService} implementation.
 *
 * <ul>
 *   <li>{@link #AZURE} — Azure Cognitive Services Translator v3 REST API.
 *       Requires {@code AZURE_TRANSLATOR_KEY} and {@code AZURE_TRANSLATOR_REGION}.
 *   <li>{@link #MYMEMORY} — MyMemory free translation API (mymemory.translated.net).
 *       No credentials required for anonymous use (5k chars/day); set
 *       {@code MYMEMORY_EMAIL} for 50k chars/day or {@code MYMEMORY_KEY} for private TM.
 *   <li>{@link #FALCON} — Placeholder for Falcon LLM translation. Not yet implemented;
 *       falls back to {@link #NONE} until Falcon translation is configured.
 *   <li>{@link #NONE} — No-op provider; returns original text unchanged.
 *       Default when no provider is configured.
 * </ul>
 */
public enum TranslationProvider {
    AZURE,
    MYMEMORY,
    FALCON,
    NONE
}
