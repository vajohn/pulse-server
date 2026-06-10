package com.edge.pulse.controllers;

import com.edge.pulse.configs.GlobalExceptionHandler;
import com.edge.pulse.data.dto.PagedResponse;
import com.edge.pulse.data.dto.spark.AwardPeriodDto;
import com.edge.pulse.data.enums.AwardPeriodStatus;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.spark.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link AdminSparkController}.
 *
 * <p>Uses standalone MockMvc — @PreAuthorize is not enforced here. Auth guard
 * coverage lives in JwtAuthenticationFilterTest and the security config.
 */
@ExtendWith(MockitoExtension.class)
class AdminSparkControllerTest {

    @Mock SparkService sparkService;
    @Mock SparkAdminService sparkAdminService;
    @Mock SparkVoteService voteService;
    @Mock SparkWinnerService winnerService;
    @Mock AuditService auditService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PERIOD_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSparkController(
                        sparkService, sparkAdminService, voteService, winnerService, auditService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(auditService.buildDetail(any(), any())).thenReturn(null);
    }

    // ── GET /award-periods ────────────────────────────────────────────────────

    @Test
    void listPeriods_noFilter_returnsPagedResponse() throws Exception {
        AwardPeriodDto dto = periodDto(AwardPeriodStatus.UPCOMING);
        when(sparkService.getPagedPeriods(0, 20, null))
                .thenReturn(new PagedResponse<>(List.of(dto), false));

        mockMvc.perform(get("/api/admin/spark/award-periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void listPeriods_withStatusFilter_returnsFilteredContent() throws Exception {
        AwardPeriodDto dto = periodDto(AwardPeriodStatus.CANCELLED);
        when(sparkService.getPagedPeriods(0, 20, AwardPeriodStatus.CANCELLED))
                .thenReturn(new PagedResponse<>(List.of(dto), false));

        mockMvc.perform(get("/api/admin/spark/award-periods")
                        .param("page", "0")
                        .param("size", "20")
                        .param("status", "CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    // ── POST /award-periods/{id}/cancel ──────────────────────────────────────

    @Test
    void cancelPeriod_validPeriod_returns200WithCancelledStatus() throws Exception {
        AwardPeriodDto dto = periodDto(AwardPeriodStatus.CANCELLED);
        when(sparkAdminService.cancelPeriod(PERIOD_ID)).thenReturn(dto);

        mockMvc.perform(post("/api/admin/spark/award-periods/{id}/cancel", PERIOD_ID)
                        .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(auditService).logAction(eq(USER_ID), eq("SPARK_PERIOD_CANCEL"),
                eq("AWARD_PERIOD"), eq(PERIOD_ID), any(), isNull());
    }

    @Test
    void cancelPeriod_terminalStatus_returns422() throws Exception {
        when(sparkAdminService.cancelPeriod(PERIOD_ID))
                .thenThrow(new IllegalStateException("Cannot cancel a period in status: ANNOUNCED"));

        mockMvc.perform(post("/api/admin/spark/award-periods/{id}/cancel", PERIOD_ID)
                        .principal(auth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Cannot cancel a period in status: ANNOUNCED"));
    }

    @Test
    void cancelPeriod_periodNotFound_returns400() throws Exception {
        when(sparkAdminService.cancelPeriod(PERIOD_ID))
                .thenThrow(new IllegalArgumentException("Award period not found: " + PERIOD_ID));

        mockMvc.perform(post("/api/admin/spark/award-periods/{id}/cancel", PERIOD_ID)
                        .principal(auth()))
                .andExpect(status().isBadRequest());
    }

    // ── POST /award-periods/{id}/advance ─────────────────────────────────────

    @Test
    void advancePeriodStatus_validPeriod_returns200() throws Exception {
        AwardPeriodDto dto = periodDto(AwardPeriodStatus.NOMINATION_OPEN);
        when(sparkAdminService.advancePeriodStatus(PERIOD_ID)).thenReturn(dto);

        mockMvc.perform(post("/api/admin/spark/award-periods/{id}/advance", PERIOD_ID)
                        .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOMINATION_OPEN"));
    }

    @Test
    void advancePeriodStatus_terminalStatus_returns422() throws Exception {
        when(sparkAdminService.advancePeriodStatus(PERIOD_ID))
                .thenThrow(new IllegalStateException("Cannot advance status from: CANCELLED"));

        mockMvc.perform(post("/api/admin/spark/award-periods/{id}/advance", PERIOD_ID)
                        .principal(auth()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AwardPeriodDto periodDto(AwardPeriodStatus status) {
        return new AwardPeriodDto(PERIOD_ID, "Test Period",
                LocalDateTime.now(), LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(8), LocalDateTime.now().plusDays(14),
                status, null, null, LocalDateTime.now());
    }

    private static UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(USER_ID, null, List.of());
    }
}
