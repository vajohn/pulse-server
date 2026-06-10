package com.edge.pulse.configs;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Falcon LLM proxy configuration.
 *
 * <p>Values are bound from {@code falcon.*} in application.yaml.
 * Set {@code falcon.api-key} (via env var {@code FALCON_API_KEY}) to enable AI features;
 * leave it empty to disable — the {@code /api/ai/chat} endpoint returns 503 when
 * the key is missing.
 */
@ConfigurationProperties(prefix = "falcon")
public record FalconProperties(
        @DefaultValue("") String apiKey,
        @DefaultValue("https://chat.falconllm.tii.ae/inference/falcon-h1-7b-instruct/v1/chat/completions")
        String endpoint,
        @DefaultValue("falcon-h1-7b-instruct") String model,
        @DefaultValue("60") int timeoutSeconds,
        @DefaultValue("1024") int maxTokens
) {
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
