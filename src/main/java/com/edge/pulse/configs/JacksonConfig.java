package com.edge.pulse.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    // Spring Boot 4 auto-configures a Jackson 3 (tools.jackson) ObjectMapper, but every
    // service here injects the Jackson 2 (com.fasterxml) ObjectMapper that used to arrive
    // transitively via the removed spring-boot-starter-security-oauth2-client. Provide it
    // explicitly so AuditService/X4AuthService/FormCacheService/etc. resolve.
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
