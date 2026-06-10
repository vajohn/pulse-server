package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AnalyticsSummaryDto;
import com.edge.pulse.services.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies Phase 1 change F-CC-03: both /team and /dashboard endpoints now accept
 * optional orgUnitId and days query parameters and forward them to AnalyticsService.
 * Previously both endpoints silently dropped these params.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerParamTest {

    private MockMvc mockMvc;

    @Mock
    private AnalyticsService analyticsService;

    private static final AnalyticsSummaryDto EMPTY_SUMMARY = new AnalyticsSummaryDto(
            0, 0.0, Map.of(), Map.of(), List.of(), false, 0, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(analyticsService)).build();
    }

    // -----------------------------------------------------------------------
    // GET /api/analytics/team
    // -----------------------------------------------------------------------

    @Test
    void getTeamAnalytics_noParams_usesDefaultDays30() throws Exception {
        when(analyticsService.getTeamAnalytics(isNull(), isNull(), eq(30))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/team"))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(isNull(), isNull(), eq(30));
    }

    @Test
    void getTeamAnalytics_customDays_forwardsToService() throws Exception {
        when(analyticsService.getTeamAnalytics(isNull(), isNull(), eq(7))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/team").param("days", "7"))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(isNull(), isNull(), eq(7));
    }

    @Test
    void getTeamAnalytics_withOrgUnitId_forwardsUuidToService() throws Exception {
        UUID orgUnitId = UUID.randomUUID();
        when(analyticsService.getTeamAnalytics(eq(orgUnitId), isNull(), eq(30))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/team")
                        .param("orgUnitId", orgUnitId.toString()))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(eq(orgUnitId), isNull(), eq(30));
    }

    @Test
    void getTeamAnalytics_withBothParams_forwardsBoth() throws Exception {
        UUID orgUnitId = UUID.randomUUID();
        when(analyticsService.getTeamAnalytics(eq(orgUnitId), isNull(), eq(90))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/team")
                        .param("orgUnitId", orgUnitId.toString())
                        .param("days", "90"))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(eq(orgUnitId), isNull(), eq(90));
    }

    @Test
    void getTeamAnalytics_invalidUuidParam_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/team")
                        .param("orgUnitId", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // GET /api/analytics/dashboard
    // -----------------------------------------------------------------------

    @Test
    void getDashboard_noParams_usesDefaultDays30() throws Exception {
        when(analyticsService.getTeamAnalytics(isNull(), isNull(), eq(30))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/dashboard"))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(isNull(), isNull(), eq(30));
    }

    @Test
    void getDashboard_withOrgUnitId_forwardsUuid() throws Exception {
        UUID orgUnitId = UUID.randomUUID();
        when(analyticsService.getTeamAnalytics(eq(orgUnitId), isNull(), eq(30))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/dashboard")
                        .param("orgUnitId", orgUnitId.toString()))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(eq(orgUnitId), isNull(), eq(30));
    }

    @Test
    void getDashboard_customDays_forwardsToService() throws Exception {
        when(analyticsService.getTeamAnalytics(isNull(), isNull(), eq(14))).thenReturn(EMPTY_SUMMARY);

        mockMvc.perform(get("/api/analytics/dashboard").param("days", "14"))
                .andExpect(status().isOk());

        verify(analyticsService).getTeamAnalytics(isNull(), isNull(), eq(14));
    }
}
