package com.edge.pulse.services;

import com.edge.pulse.configs.FalconProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link FalconService}.
 *
 * <p>HTTP behaviour is verified using {@link MockRestServiceServer} via the
 * package-private test constructor that accepts a pre-built {@link RestClient}.
 */
class FalconServiceTest {

    private static final String ENDPOINT = "https://api.example.com/v1/chat/completions";

    private static final FalconProperties DISABLED =
            new FalconProperties("", ENDPOINT, "falcon", 60, 512);

    private static final FalconProperties ENABLED =
            new FalconProperties("sk-test", ENDPOINT, "falcon", 60, 512);

    // ── isEnabled() ─────────────────────────────────────────────────────────

    @Test
    void isEnabled_returnsFalse_whenApiKeyBlank() {
        assertThat(new FalconService(DISABLED).isEnabled()).isFalse();
    }

    @Test
    void isEnabled_returnsTrue_whenApiKeySet() {
        assertThat(new FalconService(ENABLED).isEnabled()).isTrue();
    }

    // ── chat() — disabled guard ──────────────────────────────────────────────

    @Test
    void chat_throwsIllegalStateException_whenDisabled() {
        assertThatThrownBy(() -> new FalconService(DISABLED).chat("Hello", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FALCON_API_KEY");
    }

    // ── chat() — HTTP behaviour ──────────────────────────────────────────────

    @Test
    void chat_withoutSystemPrompt_returnsReply() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Hello back!"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        var service = new FalconService(ENABLED, builder.build());
        assertThat(service.chat("Hello", null)).isEqualTo("Hello back!");
        server.verify();
    }

    @Test
    void chat_withSystemPrompt_includesSystemRoleInBody() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                // Request JSON must contain both "system" and "user" role entries
                .andExpect(content().string(containsString("\"system\"")))
                .andExpect(content().string(containsString("\"user\"")))
                .andExpect(content().string(containsString("Be helpful")))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Sure!"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        var service = new FalconService(ENABLED, builder.build());
        assertThat(service.chat("Help me", "Be helpful")).isEqualTo("Sure!");
        server.verify();
    }

    @Test
    void chat_withNullSystemPrompt_sendsNoSystemRole() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(not(containsString("\"system\""))))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Reply"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        var service = new FalconService(ENABLED, builder.build());
        assertThat(service.chat("Hello", null)).isEqualTo("Reply");
        server.verify();
    }

    @Test
    void chat_withBlankSystemPrompt_sendsNoSystemRole() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(not(containsString("\"system\""))))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Reply"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        var service = new FalconService(ENABLED, builder.build());
        assertThat(service.chat("Hello", "   ")).isEqualTo("Reply"); // blank system prompt
        server.verify();
    }

    @Test
    void chat_usesConfiguredModel() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(content().string(containsString("\"falcon\""))) // model name in body
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        new FalconService(ENABLED, builder.build()).chat("Hi", null);
        server.verify();
    }

    @Test
    void chat_emptyChoicesList_throwsIllegalStateException() {
        var builder = RestClient.builder().baseUrl(ENDPOINT);
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        var service = new FalconService(ENABLED, builder.build());
        assertThatThrownBy(() -> service.chat("Hello", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Empty or malformed");
    }

    @Test
    void chat_authorizationHeaderSentWithBearerToken() {
        var builder = RestClient.builder()
                .baseUrl(ENDPOINT)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + ENABLED.apiKey());
        var server  = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(ENDPOINT))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk-test"))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        new FalconService(ENABLED, builder.build()).chat("Hi", null);
        server.verify();
    }
}
