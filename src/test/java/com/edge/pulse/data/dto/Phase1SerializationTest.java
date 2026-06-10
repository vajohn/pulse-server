package com.edge.pulse.data.dto;

import com.edge.pulse.data.dto.spark.NomineeInfoDto;
import com.edge.pulse.data.dto.spark.SparkCategoryDto;
import com.edge.pulse.data.dto.spark.SparkWinnerDto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Phase 1 serialization fixes:
 *   F-CC-14: SessionDto.isAnonymous field name must not be stripped to "anonymous" by Jackson
 *   F-CC-16: SparkWinnerDto.awardPoints must be a JSON string, not a float
 *   F-CC-05: ApiError must include an "errors" array field
 */
class Phase1SerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // -----------------------------------------------------------------------
    // F-CC-14 — SessionDto.isAnonymous
    // -----------------------------------------------------------------------

    @Test
    void sessionDto_isAnonymous_true_serializesAsIsAnonymous() throws Exception {
        SessionDto dto = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                true,
                LocalDateTime.now(), null,
                List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        // Must be present as "isAnonymous", NOT stripped to "anonymous"
        assertThat(node.has("isAnonymous"))
                .as("field must be named 'isAnonymous', not 'anonymous'")
                .isTrue();
        assertThat(node.get("isAnonymous").asBoolean()).isTrue();
        assertThat(node.has("anonymous"))
                .as("'anonymous' must not appear — would be the wrong name without @JsonProperty")
                .isFalse();
    }

    @Test
    void sessionDto_isAnonymous_false_serializesCorrectly() throws Exception {
        SessionDto dto = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                false,
                LocalDateTime.now(), null,
                List.of());

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.has("isAnonymous")).isTrue();
        assertThat(node.get("isAnonymous").asBoolean()).isFalse();
    }

    @Test
    void sessionDto_isAnonymous_roundTrips_correctly() throws Exception {
        SessionDto original = new SessionDto(
                UUID.randomUUID(), UUID.randomUUID(),
                true,
                LocalDateTime.now(), null,
                List.of());

        String json = mapper.writeValueAsString(original);
        SessionDto deserialized = mapper.readValue(json, SessionDto.class);

        assertThat(deserialized.isAnonymous()).isTrue();
    }

    // -----------------------------------------------------------------------
    // F-CC-16 — SparkWinnerDto.awardPoints as JSON string
    // -----------------------------------------------------------------------

    @Test
    void sparkWinnerDto_awardPoints_serializesAsJsonString_notNumber() throws Exception {
        SparkWinnerDto dto = new SparkWinnerDto(
                UUID.randomUUID(), UUID.randomUUID(),
                "Q1 2026",
                new SparkCategoryDto("cat-1", "Innovation", "Innovative spirit", "star", 1, true),
                new NomineeInfoDto(UUID.randomUUID(), "Alice Smith", "alice@example.com", "Engineer", "Backend"),
                42,
                LocalDateTime.now(),
                new BigDecimal("500.00"),
                10L);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("awardPoints").isTextual())
                .as("awardPoints must be a JSON string to avoid float precision loss on mobile")
                .isTrue();
        assertThat(node.get("awardPoints").asText()).isEqualTo("500.00");
    }

    @Test
    void sparkWinnerDto_awardPoints_preservesDecimalPrecision() throws Exception {
        SparkWinnerDto dto = new SparkWinnerDto(
                UUID.randomUUID(), UUID.randomUUID(), "Q1",
                new SparkCategoryDto("cat-2", "Cat", "Desc", "icon", 2, true),
                new NomineeInfoDto(UUID.randomUUID(), "Bob", "b@e.com", "Manager", "Ops"),
                1, LocalDateTime.now(),
                new BigDecimal("1234.56"),
                0L);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(node.get("awardPoints").asText()).isEqualTo("1234.56");
    }

    // -----------------------------------------------------------------------
    // F-CC-05 — ApiError errors array
    // -----------------------------------------------------------------------

    @Test
    void apiError_twoArgConstructor_hasEmptyErrorsArray() throws Exception {
        ApiError error = new ApiError(400, "bad request");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(error));

        assertThat(node.has("errors")).as("ApiError must always include 'errors' field").isTrue();
        assertThat(node.get("errors").isArray()).isTrue();
        assertThat(node.get("errors").size()).isZero();
        assertThat(node.get("status").asInt()).isEqualTo(400);
        assertThat(node.get("message").asText()).isEqualTo("bad request");
        assertThat(node.has("timestamp")).isTrue();
    }

    @Test
    void apiError_threeArgConstructor_populatesFieldErrors() throws Exception {
        List<ApiError.FieldError> fieldErrors = List.of(
                new ApiError.FieldError("email", "must not be blank"),
                new ApiError.FieldError("name", "size must be between 1 and 100"));
        ApiError error = new ApiError(400, "Validation failed", fieldErrors);

        JsonNode node = mapper.readTree(mapper.writeValueAsString(error));

        assertThat(node.get("errors").size()).isEqualTo(2);
        assertThat(node.get("errors").get(0).get("field").asText()).isEqualTo("email");
        assertThat(node.get("errors").get(0).get("message").asText()).isEqualTo("must not be blank");
        assertThat(node.get("errors").get(1).get("field").asText()).isEqualTo("name");
    }

    @Test
    void apiError_fieldError_hasFieldAndMessageOnly() throws Exception {
        ApiError.FieldError fe = new ApiError.FieldError("username", "too short");

        JsonNode node = mapper.readTree(mapper.writeValueAsString(fe));

        assertThat(node.has("field")).isTrue();
        assertThat(node.has("message")).isTrue();
        // No extra fields should leak from the record
        assertThat(node.size()).isEqualTo(2);
    }
}
