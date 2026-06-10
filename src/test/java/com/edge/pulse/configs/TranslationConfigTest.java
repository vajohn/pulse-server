package com.edge.pulse.configs;

import com.edge.pulse.data.enums.TranslationProvider;
import com.edge.pulse.services.translation.CachingTranslationService;
import com.edge.pulse.services.translation.TranslationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationConfigTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RestClient.Builder builder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TranslationConfig config = new TranslationConfig();

    @Test
    void translationService_providerNone_wrapsNullServiceInCaching() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.NONE);

        TranslationService service = config.translationService(props, redisTemplate, builder, objectMapper);

        assertThat(service).isInstanceOf(CachingTranslationService.class);
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.NONE);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void translationService_providerMyMemory_wrapsMyMemoryServiceInCaching() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.MYMEMORY);
        // No credentials required — anonymous mode

        TranslationService service = config.translationService(props, redisTemplate, builder, objectMapper);

        assertThat(service).isInstanceOf(CachingTranslationService.class);
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.MYMEMORY);
        assertThat(service.isAvailable()).isTrue(); // always true for MyMemory
    }

    @Test
    void translationService_providerFalcon_wrapsNullServiceInCaching() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.FALCON);

        TranslationService service = config.translationService(props, redisTemplate, builder, objectMapper);

        // FALCON not yet implemented — falls back to NullTranslationService
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.NONE);
        assertThat(service.isAvailable()).isFalse();
    }

    @Test
    void translationService_providerAzure_wrapsAzureServiceInCaching() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.AZURE);
        props.getAzure().setKey("key");
        props.getAzure().setRegion("eastus");

        when(builder.baseUrl(any(String.class))).thenReturn(builder);
        when(builder.build()).thenReturn(mock(RestClient.class));
        TranslationService service = config.translationService(props, redisTemplate, builder, objectMapper);

        assertThat(service).isInstanceOf(CachingTranslationService.class);
        assertThat(service.getProvider()).isEqualTo(TranslationProvider.AZURE);
        assertThat(service.isAvailable()).isTrue();
    }

    @Test
    void assertTrueValidation_azureWithEmptyKey_failsValidation() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.AZURE);
        props.getAzure().setKey("");
        props.getAzure().setRegion("eastus");

        assertThat(props.isAzureConfigValid()).isFalse();
    }

    @Test
    void assertTrueValidation_noneProvider_alwaysPasses() {
        TranslationProperties props = new TranslationProperties();
        props.setProvider(TranslationProvider.NONE);
        // credentials empty — should still pass for NONE

        assertThat(props.isAzureConfigValid()).isTrue();
    }
}
