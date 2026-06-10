package com.edge.pulse.services;

import com.edge.pulse.configs.FalconProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxies chat requests to the Falcon LLM API using the OpenAI-compatible
 * {@code /v1/chat/completions} format.
 *
 * <p>The service is enabled only when {@code falcon.api-key} is configured;
 * {@link #isEnabled()} can be checked before calling {@link #chat}.
 */
@Service
@Slf4j
public class FalconService {

    // ── Internal OpenAI-compatible types ────────────────────────────────────

    private record FalconMessage(String role, String content) {}

    private record FalconRequest(
            String model,
            List<FalconMessage> messages,
            @JsonProperty("max_tokens") int maxTokens
    ) {}

    private record FalconChoice(FalconMessage message) {}

    private record FalconResponse(List<FalconChoice> choices) {}

    // ── State ────────────────────────────────────────────────────────────────

    private final FalconProperties props;

    /**
     * {@code null} when Falcon is not configured — checked by {@link #isEnabled()}.
     */
    private final RestClient client;

    /** Package-private constructor for tests — injects a pre-built {@link RestClient}. */
    FalconService(FalconProperties props, RestClient testClient) {
        this.props = props;
        this.client = testClient;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FalconService(FalconProperties props) {
        this.props = props;

        if (props.isEnabled()) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofSeconds(10));
            factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));

            this.client = RestClient.builder()
                    .requestFactory(factory)
                    .baseUrl(props.endpoint())
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            log.info("[FalconService] Falcon AI proxy enabled (model={})", props.model());
        } else {
            this.client = null;
            log.info("[FalconService] Falcon AI proxy disabled — set FALCON_API_KEY to enable");
        }
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    /**
     * Sends a chat message to Falcon and returns the AI reply text.
     *
     * @param userMessage  The user's message.
     * @param systemPrompt Optional system prompt; ignored when blank or {@code null}.
     * @return The AI-generated reply string.
     * @throws IllegalStateException if called when Falcon is not configured.
     */
    public String chat(String userMessage, String systemPrompt) {
        if (client == null) {
            throw new IllegalStateException("Falcon AI is not configured. Set FALCON_API_KEY to enable AI features.");
        }

        var messages = new ArrayList<FalconMessage>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new FalconMessage("system", systemPrompt));
        }
        messages.add(new FalconMessage("user", userMessage));

        var requestBody = new FalconRequest(props.model(), messages, props.maxTokens());

        log.debug("[FalconService] Sending chat request (messageLen={})", userMessage.length());

        FalconResponse response = client.post()
                .body(requestBody)
                .retrieve()
                .body(FalconResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("Empty or malformed response from Falcon AI");
        }

        String reply = response.choices().getFirst().message().content();
        log.debug("[FalconService] Received reply (replyLen={})", reply != null ? reply.length() : 0);
        return reply;
    }
}
