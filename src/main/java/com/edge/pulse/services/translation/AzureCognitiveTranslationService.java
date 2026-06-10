package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Azure Cognitive Services Translator v3 implementation of {@link TranslationService}.
 *
 * <p>API contract:
 * <pre>
 * POST {endpoint}/translate?api-version=3.0&amp;from={from}&amp;to={to}
 * Headers: Ocp-Apim-Subscription-Key, Ocp-Apim-Subscription-Region
 * Body:    [{"Text":"text1"},{"Text":"text2"},...]   (up to 50 items per call)
 * Response:[{"translations":[{"text":"...","to":"ar"}]},...]
 * </pre>
 *
 * <p>On any {@link RestClientException}: logs WARN and returns the original text(s) —
 * translation failure must never block a save operation.
 *
 * <p>A {@link PostConstruct} warning is emitted when credentials are empty so that
 * misconfiguration is visible in the boot log even if {@code @AssertTrue} validation
 * passed (e.g. credentials were set but are empty after env var expansion).
 */
@Slf4j
public class AzureCognitiveTranslationService implements TranslationService {

    private static final String API_VERSION = "3.0";
    private static final int MAX_BATCH_SIZE = 50;

    private final TranslationProperties.Azure config;
    private final RestClient restClient;

    public AzureCognitiveTranslationService(TranslationProperties.Azure config,
                                             RestClient.Builder builder) {
        this.config = config;
        this.restClient = builder
                .baseUrl(config.getEndpoint())
                .build();
    }

    @PostConstruct
    void warnIfNotAvailable() {
        if (!isAvailable()) {
            log.warn("TranslationProvider is AZURE but key/region are empty — " +
                     "translate calls will return original text. " +
                     "Set AZURE_TRANSLATOR_KEY and AZURE_TRANSLATOR_REGION.");
        }
    }

    @Override
    public String translate(String text, String fromLocale, String toLocale) {
        List<String> results = translateBatch(List.of(text), fromLocale, toLocale);
        return results.isEmpty() ? text : results.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> translateBatch(List<String> texts, String fromLocale, String toLocale) {
        if (!isAvailable()) {
            log.debug("Azure translator not configured — returning original texts");
            return texts;
        }
        if (texts.size() > MAX_BATCH_SIZE) {
            log.warn("translateBatch called with {} items (max {}); truncating",
                    texts.size(), MAX_BATCH_SIZE);
            texts = texts.subList(0, MAX_BATCH_SIZE);
        }

        List<Map<String, String>> body = texts.stream()
                .map(t -> Map.of("Text", t))
                .toList();
        try {
            List<Map<String, Object>> response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/translate")
                            .queryParam("api-version", API_VERSION)
                            .queryParam("from", fromLocale)
                            .queryParam("to", toLocale)
                            .build())
                    .header("Ocp-Apim-Subscription-Key", config.getKey())
                    .header("Ocp-Apim-Subscription-Region", config.getRegion())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(List.class);

            if (response == null || response.size() != texts.size()) {
                log.warn("Azure translator returned unexpected response size; returning originals");
                return texts;
            }

            final List<String> finalTexts = texts;
            return java.util.stream.IntStream.range(0, response.size())
                    .mapToObj(i -> {
                        try {
                            List<Map<String, Object>> translations =
                                    (List<Map<String, Object>>) response.get(i).get("translations");
                            if (translations != null && !translations.isEmpty()) {
                                return (String) translations.get(0).get("text");
                            }
                        } catch (Exception e) {
                            log.warn("Failed to parse translation at index {}", i, e);
                        }
                        return finalTexts.get(i);
                    })
                    .toList();

        } catch (RestClientException e) {
            log.warn("Azure translation request failed — returning original texts: {}", e.getMessage());
            return texts;
        }
    }

    @Override
    public boolean isAvailable() {
        return config.getKey() != null && !config.getKey().isBlank()
            && config.getRegion() != null && !config.getRegion().isBlank();
    }

    @Override
    public TranslationProvider getProvider() {
        return TranslationProvider.AZURE;
    }
}
