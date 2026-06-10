package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AzureCognitiveTranslationServiceTest {

    @Mock private RestClient.Builder restClientBuilder;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec uriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private AzureCognitiveTranslationService service;
    private TranslationProperties.Azure config;

    @BeforeEach
    void setUp() {
        config = new TranslationProperties.Azure();
        config.setKey("test-key");
        config.setRegion("eastus");
        config.setEndpoint("https://api.cognitive.microsofttranslator.com");

        when(restClientBuilder.baseUrl(any(String.class))).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        service = new AzureCognitiveTranslationService(config, restClientBuilder);
    }

    // ── isAvailable ────────────────────────────────────────────────────────────

    @Test
    void isAvailable_withCredentials_returnsTrue() {
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void isAvailable_emptyKey_returnsFalse() {
        config.setKey("");
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_emptyRegion_returnsFalse() {
        config.setRegion("");
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_nullKey_returnsFalse() {
        config.setKey(null);
        assertThat(service.isAvailable()).isFalse();
    }

    // ── provider ───────────────────────────────────────────────────────────────

    @Test
    void getProvider_returnsAzure() {
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.AZURE);
    }

    // ── successful translation ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void translateBatch_success_returnsTranslatedTexts() {
        List<Map<String, Object>> azureResponse = List.of(
            Map.of("translations", List.of(Map.of("text", "مرحبا", "to", "ar"))),
            Map.of("translations", List.of(Map.of("text", "مع السلامة", "to", "ar")))
        );
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(List.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(List.class)).thenReturn(azureResponse);

        List<String> result = service.translateBatch(
                List.of("Hello", "Goodbye"), "en", "ar");

        assertThat(result).containsExactly("مرحبا", "مع السلامة");
    }

    @SuppressWarnings("unchecked")
    @Test
    void translate_delegatesToBatch_returnsSingleResult() {
        List<Map<String, Object>> azureResponse = List.of(
            Map.of("translations", List.of(Map.of("text", "مرحبا", "to", "ar")))
        );
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(List.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(List.class)).thenReturn(azureResponse);

        String result = service.translate("Hello", "en", "ar");

        assertThat(result).isEqualTo("مرحبا");
    }

    // ── failure returns originals ──────────────────────────────────────────────

    @Test
    void translateBatch_restClientException_returnsOriginalTexts() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(List.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(List.class)).thenThrow(new RestClientException("network error"));

        List<String> result = service.translateBatch(List.of("Hello", "World"), "en", "ar");

        assertThat(result).containsExactly("Hello", "World");
    }

    @Test
    void translate_restClientException_returnsOriginalText() {
        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(java.util.function.Function.class))).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.body(any(List.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(List.class)).thenThrow(new RestClientException("timeout"));

        String result = service.translate("Hello", "en", "ar");

        assertThat(result).isEqualTo("Hello");
    }

    // ── not available → returns originals without API call ────────────────────

    @Test
    void translateBatch_notAvailable_returnsOriginalsWithoutApiCall() {
        config.setKey("");

        List<String> result = service.translateBatch(List.of("Hello"), "en", "ar");

        assertThat(result).containsExactly("Hello");
        verifyNoInteractions(restClient);
    }

    // ── @PostConstruct warning ─────────────────────────────────────────────────

    @Test
    void warnIfNotAvailable_emptyCredentials_doesNotThrow() {
        config.setKey("");
        // Should log WARN but not throw
        service.warnIfNotAvailable();
    }
}
