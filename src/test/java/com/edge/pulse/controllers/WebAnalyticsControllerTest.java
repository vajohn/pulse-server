package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.EngagementSummaryDto;
import com.edge.pulse.services.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link WebAnalyticsController} (PULSE-WEB-4).
 *
 * <p>Verifies param wiring (scopeLevel/nodeId/includeChildren/days), the JSON
 * shape WEB-5/6 will code against, and the masked-result contract. As with the
 * other standalone controller tests in this codebase, @PreAuthorize is NOT
 * evaluated here — authorization (REPORT_VIEW 200 / others 403) is asserted
 * separately in {@link WebAnalyticsControllerAuthTest}.
 */
@ExtendWith(MockitoExtension.class)
class WebAnalyticsControllerTest {

    private MockMvc mockMvc;

    @Mock private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        WebAnalyticsController controller = new WebAnalyticsController(analyticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void engagement_defaults_delegateWithNullScopeAndDefaults() throws Exception {
        EngagementSummaryDto dto = new EngagementSummaryDto(
                "GLOBAL", null, null, true, 30,
                false, 50, 100, 50.0, 3.9,
                List.of(new EngagementSummaryDto.CategoryScore("Pulse Q1", 3.9, 50)),
                List.of(new EngagementSummaryDto.ScoreBucket(4, 30L)),
                new EngagementSummaryDto.Trend(3.9, 3.5, 0.4, "UP"),
                null);
        // userId null (no @AuthenticationPrincipal in standalone), scopeLevel null, defaults applied
        when(analyticsService.getEngagementSummary(isNull(), isNull(), eq(true), eq(30), isNull()))
                .thenReturn(dto);

        mockMvc.perform(get("/api/admin/analytics/engagement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.overallScore").value(3.9))
                .andExpect(jsonPath("$.participationRate").value(50.0))
                .andExpect(jsonPath("$.categoryScores[0].category").value("Pulse Q1"))
                .andExpect(jsonPath("$.scoreDistribution[0].score").value(4))
                .andExpect(jsonPath("$.trend.direction").value("UP"))
                // M-2: enps is @JsonInclude(NON_NULL) and always null today, so the field is
                // genuinely ABSENT from the JSON (not serialized as "enps":null) — WEB-5 should
                // treat its absence as "not yet supported", not "computed-but-zero".
                .andExpect(jsonPath("$.enps").doesNotExist());

        verify(analyticsService).getEngagementSummary(isNull(), isNull(), eq(true), eq(30), isNull());
    }

    @Test
    void engagement_withParams_passesThemThrough() throws Exception {
        UUID nodeId = UUID.randomUUID();
        EngagementSummaryDto dto = new EngagementSummaryDto(
                "ENTITY", nodeId, "Ops", false, 7,
                false, 6, 6, 100.0, 3.0,
                List.of(), List.of(),
                new EngagementSummaryDto.Trend(3.0, null, null, "NO_PRIOR_DATA"),
                null);
        when(analyticsService.getEngagementSummary(eq("ENTITY"), eq(nodeId), eq(false), eq(7), isNull()))
                .thenReturn(dto);

        mockMvc.perform(get("/api/admin/analytics/engagement")
                        .param("scopeLevel", "ENTITY")
                        .param("nodeId", nodeId.toString())
                        .param("includeChildren", "false")
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopeLevel").value("ENTITY"))
                .andExpect(jsonPath("$.includeChildren").value(false))
                .andExpect(jsonPath("$.periodDays").value(7))
                .andExpect(jsonPath("$.trend.direction").value("NO_PRIOR_DATA"));

        verify(analyticsService).getEngagementSummary(eq("ENTITY"), eq(nodeId), eq(false), eq(7), isNull());
    }

    @Test
    void engagement_maskedResult_exposesNoAggregates() throws Exception {
        UUID nodeId = UUID.randomUUID();
        EngagementSummaryDto masked = EngagementSummaryDto.masked("TEAM", nodeId, "Small Team", true, 30);
        when(analyticsService.getEngagementSummary(any(), any(), any(Boolean.class), any(Integer.class), any()))
                .thenReturn(masked);

        mockMvc.perform(get("/api/admin/analytics/engagement")
                        .param("scopeLevel", "TEAM")
                        .param("nodeId", nodeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(true))
                .andExpect(jsonPath("$.respondents").doesNotExist())
                .andExpect(jsonPath("$.overallScore").doesNotExist())
                .andExpect(jsonPath("$.categoryScores").doesNotExist());
    }
}
