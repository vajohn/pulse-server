package com.edge.pulse.configs;

import com.edge.pulse.data.enums.TranslationProvider;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for the Pulse translation service.
 *
 * <p>All settings have sensible defaults so the service starts without any environment
 * variables (defaulting to provider=NONE which returns text unchanged). Switch to
 * a real provider by setting {@code TRANSLATION_PROVIDER} and the corresponding
 * credential env vars.
 *
 * <ul>
 *   <li>{@code TRANSLATION_PROVIDER} — which backend to use (AZURE, MYMEMORY, FALCON, NONE).
 *   <li>{@code TRANSLATION_CACHE_TTL_DAYS} — how long to cache translated strings in Redis.
 *   <li>{@code TRANSLATION_DAILY_CHAR_BUDGET} — max characters per user per day
 *       (cost-control guard — not a security boundary).
 *       Set to 5000 for MyMemory anonymous mode, 50000 for MyMemory registered mode.
 *   <li>{@code AZURE_TRANSLATOR_KEY} — required only when provider=AZURE.
 *   <li>{@code AZURE_TRANSLATOR_REGION} — required only when provider=AZURE.
 *   <li>{@code MYMEMORY_EMAIL} — optional; raises MyMemory quota from 5k to 50k chars/day.
 *   <li>{@code MYMEMORY_KEY} — optional; enables MyMemory private TM and higher quotas.
 * </ul>
 *
 * <p><strong>Future DB-backed config:</strong> when a {@code system_config} table and
 * Redis-cached {@code ConfigService} are added, {@code TranslationConfig} reads from
 * {@code ConfigService} instead of this properties bean — callers are unaffected because
 * the factory is the only place that reads the provider selection.
 */
@Configuration
@ConfigurationProperties(prefix = "pulse.translation")
@Validated
@Getter
@Setter
public class TranslationProperties {

    /** Active translation provider. Default NONE — returns original text unchanged. */
    private TranslationProvider provider = TranslationProvider.NONE;

    /**
     * Translation result Redis cache TTL. Default 7 days.
     * Cached independently per text so the same phrase hits the cache across forms.
     * Must be ≥ 1 day.
     */
    @Min(value = 1, message = "pulse.translation.cache-ttl-days must be at least 1")
    private int cacheTtlDays = 7;

    /**
     * Per-user daily character budget for the translate endpoint.
     * Prevents cost-injection abuse via unlimited external API calls.
     * Default 50,000 chars/day. Must be ≥ 1.
     */
    @Min(value = 1, message = "pulse.translation.daily-char-budget must be at least 1")
    private int dailyCharBudget = 50_000;

    private Azure azure = new Azure();
    private MyMemory myMemory = new MyMemory();

    @Getter
    @Setter
    public static class Azure {
        // No @NotBlank here — credentials are only required when provider = AZURE.
        // Cross-field validation is performed by isAzureConfigValid() below.
        private String key = "";         // ${AZURE_TRANSLATOR_KEY}
        private String region = "";      // ${AZURE_TRANSLATOR_REGION}
        private String endpoint = "https://api.cognitive.microsofttranslator.com";
    }

    @Getter
    @Setter
    public static class MyMemory {
        /**
         * Optional registered email — raises daily quota from 5k to 50k chars/day.
         * Leave blank for anonymous (5k chars/day). Set via {@code MYMEMORY_EMAIL}.
         */
        private String email = "";

        /**
         * Optional API key for private TM access and higher quotas.
         * Leave blank for anonymous usage. Set via {@code MYMEMORY_KEY}.
         */
        private String key = "";
    }

    /**
     * Cross-field validation: Azure credentials are required only when provider = AZURE.
     * Runs at startup via {@code @Validated} — gives a clear error message rather than
     * a NullPointerException when the service is called with empty credentials.
     */
    @AssertTrue(message =
        "pulse.translation.azure.key and azure.region are required when provider is AZURE")
    public boolean isAzureConfigValid() {
        return provider != TranslationProvider.AZURE
            || (azure.key != null && !azure.key.isBlank()
                && azure.region != null && !azure.region.isBlank());
    }

    /** Convenience Duration accessor for cache TTL. */
    public Duration cacheTtl() {
        return Duration.ofDays(cacheTtlDays);
    }
}
