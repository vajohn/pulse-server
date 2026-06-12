package com.edge.pulse.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    // Restores the RestClient.Builder that used to arrive transitively via the removed
    // spring-boot-starter-security-oauth2-client. Prototype scope mirrors Spring Boot's own
    // RestClientAutoConfiguration so each injectee (X4AuthService, SafReconClient, …) gets a
    // fresh builder it can mutate (e.g. baseUrl) without affecting others.
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
