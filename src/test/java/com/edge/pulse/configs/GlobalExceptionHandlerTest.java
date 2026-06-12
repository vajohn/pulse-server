package com.edge.pulse.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies Phase 1 change F-CC-05: GlobalExceptionHandler returns field-level
 * errors in an "errors" array instead of collapsing all messages into one string.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @RestController
    static class TestController {
        record CreateRequest(
                @NotBlank(message = "name must not be blank") String name,
                @NotNull(message = "count is required") Integer count,
                @Size(min = 5, max = 20, message = "code must be between 5 and 20 characters") String code
        ) {}

        @PostMapping("/test/validate")
        void create(@RequestBody @Valid CreateRequest req) {}

        @GetMapping("/test/not-found")
        void throwNotFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/test/forbidden")
        void throwForbidden() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        @GetMapping("/test/required-param")
        String requiredParam(@RequestParam UUID periodId) { return periodId.toString(); }
    }

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void singleFieldViolation_returnsErrors_arrayWithOneEntry() throws Exception {
        // name is blank — triggers @NotBlank
        String body = """
                {"name": "", "count": 5, "code": "abcde"}
                """;

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("name must not be blank"));
    }

    @Test
    void multipleFieldViolations_returnsErrors_arrayWithAllEntries() throws Exception {
        // name blank + count null — both @NotBlank and @NotNull fire
        String body = """
                {"name": "", "count": null, "code": "abcde"}
                """;

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                // Two violations — each in its own errors[] entry
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "count")))
                .andExpect(jsonPath("$.errors[*].message",
                        containsInAnyOrder("name must not be blank", "count is required")));
    }

    @Test
    void validationError_topLevelMessage_isSummaryOfFieldMessages() throws Exception {
        String body = """
                {"name": "", "count": null, "code": "abcde"}
                """;

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                // Top-level message is a comma-joined summary (for backwards compat / logging)
                .andExpect(jsonPath("$.message", containsString("name must not be blank")))
                .andExpect(jsonPath("$.message", containsString("count is required")));
    }

    @Test
    void validRequest_returnsOk() throws Exception {
        String body = """
                {"name": "Alice", "count": 3, "code": "abcde"}
                """;

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void illegalArgumentException_returns400_withMessage() throws Exception {
        // Malformed JSON triggers HttpMessageNotReadableException → 400, no errors array
        String body = "not valid json";

        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void responseStatusException_notFound_returns404_notGeneric500() throws Exception {
        // Before the ResponseStatusException handler was added, this returned 500.
        // Regression guard: must return 404 with correct status field.
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void responseStatusException_forbidden_returns403_notGeneric500() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // ── Task 5: missing / mistyped @RequestParam → 400 ──────────────────────

    @Test
    void missingRequiredParam_returns400() throws Exception {
        mockMvc.perform(get("/test/required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformedUuidParam_returns400() throws Exception {
        mockMvc.perform(get("/test/required-param").param("periodId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
