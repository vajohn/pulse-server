package com.edge.pulse.configs;

import com.edge.pulse.services.translation.AzureCognitiveTranslationService;
import com.edge.pulse.services.translation.CachingTranslationService;
import com.edge.pulse.services.translation.MyMemoryTranslationService;
import com.edge.pulse.services.translation.NullTranslationService;
import com.edge.pulse.services.translation.TranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

/**
 * Factory that wires the correct {@link TranslationService} implementation based on
 * {@link TranslationProperties#getProvider()}.
 *
 * <p>This is the <strong>single point of change</strong> when adding a new provider or
 * switching to DB-backed config — no callers are touched. The selected raw service is
 * wrapped in a {@link CachingTranslationService} Redis decorator so that every
 * implementation benefits from per-text translation caching automatically.
 *
 * <p><strong>Future DB-backed config:</strong> replace {@code props.getProvider()} with
 * a {@code ConfigService.get("translation.provider")} call here. Callers are unaffected.
 */
@Slf4j
@Configuration
public class TranslationConfig {

    /**
     * Return type is {@code CachingTranslationService} (not the interface) so that
     * {@link com.edge.pulse.controllers.AdminTranslationController} can inject it directly
     * and call {@link CachingTranslationService#translateSingle} to obtain the accurate
     * {@code cached} flag for the API response. All other callers declare the field as
     * {@link TranslationService} and are unaffected.
     */
    @Bean
    public CachingTranslationService translationService(TranslationProperties props,
                                                        StringRedisTemplate redisTemplate,
                                                        RestClient.Builder builder,
                                                        ObjectMapper objectMapper) {
        TranslationService raw = switch (props.getProvider()) {
            case AZURE     -> new AzureCognitiveTranslationService(props.getAzure(), builder);
            case MYMEMORY  -> {
                if (props.getMyMemory().getEmail().isBlank() && props.getDailyCharBudget() > 5_000) {
                    log.warn("MyMemory anonymous mode allows 5k chars/day but " +
                             "TRANSLATION_DAILY_CHAR_BUDGET={} — the per-user budget guard will not " +
                             "prevent API quota exhaustion before the daily limit is hit. " +
                             "Set MYMEMORY_EMAIL for 50k chars/day, or set " +
                             "TRANSLATION_DAILY_CHAR_BUDGET=5000 to match the anonymous tier.",
                             props.getDailyCharBudget());
                }
                yield new MyMemoryTranslationService(props.getMyMemory(), objectMapper);
            }
            case FALCON    -> new NullTranslationService(); // placeholder until Falcon translation is configured
            case NONE      -> new NullTranslationService();
        };
        return new CachingTranslationService(raw, redisTemplate, props);
    }
}
