package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.OrgUnitNodeDto;
import com.edge.pulse.data.dto.SurveyReportDto;
import com.edge.pulse.data.enums.OrgLevel;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc tests for AdminReportController — verifies that the
 * controller correctly passes orgUnitId and userId to AnalyticsService,
 * and returns the expected JSON shape.
 *
 * Note: @PreAuthorize annotations are NOT evaluated in standalone MockMvc;
 * role-based access control is tested in AnalyticsServiceTest.
 * In standalone mode there is no security context, so authentication is null
 * and canViewText defaults to false.
 */
@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    private MockMvc mockMvc;

    @Mock private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        // We need all deps of AdminReportController — inject nulls for unused ones
        AdminReportController controller = new AdminReportController(analyticsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // -----------------------------------------------------------------------
    // GET /api/admin/reports/form/{id}
    // -----------------------------------------------------------------------

    @Test
    void getSurveyReport_noOrgUnit_delegatesWithNullOrgUnitId() throws Exception {
        UUID surveyId = UUID.randomUUID();
        SurveyReportDto dto = minimalReport(surveyId, 10L, 8L, true);

        // No @AuthenticationPrincipal in standalone mode → userId = null; authentication null → canViewText = false
        when(analyticsService.getSurveyReport(eq(surveyId), isNull(), isNull(), anyBoolean())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/reports/form/{id}", surveyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surveyId").value(surveyId.toString()))
                .andExpect(jsonPath("$.completedSessions").value(8))
                .andExpect(jsonPath("$.privacyThresholdMet").value(true));

        verify(analyticsService).getSurveyReport(eq(surveyId), isNull(), isNull(), anyBoolean());
    }

    @Test
    void getSurveyReport_withOrgUnitId_passesOrgUnitIdToService() throws Exception {
        UUID surveyId = UUID.randomUUID();
        UUID orgUnitId = UUID.randomUUID();
        SurveyReportDto dto = minimalReport(surveyId, 10L, 5L, true);

        when(analyticsService.getSurveyReport(eq(surveyId), eq(orgUnitId), isNull(), anyBoolean())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/reports/form/{id}", surveyId)
                        .param("orgUnitId", orgUnitId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedSessions").value(5));

        verify(analyticsService).getSurveyReport(eq(surveyId), eq(orgUnitId), isNull(), anyBoolean());
    }

    @Test
    void getSurveyReport_anonymousAndIdentifiedFieldsPresent() throws Exception {
        UUID surveyId = UUID.randomUUID();
        SurveyReportDto dto = new SurveyReportDto(
                surveyId, "Test Survey",
                1L, 20L,
                15L, 3L,
                75.0, true,
                List.of(), 6L, 9L,
                List.of());

        when(analyticsService.getSurveyReport(eq(surveyId), isNull(), isNull(), anyBoolean())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/reports/form/{id}", surveyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousSessions").value(6))
                .andExpect(jsonPath("$.identifiedSessions").value(9))
                .andExpect(jsonPath("$.completionRate").value(75.0));
    }

    @Test
    void getSurveyReport_privacyShield_thresholdNotMet() throws Exception {
        UUID surveyId = UUID.randomUUID();
        SurveyReportDto dto = minimalReport(surveyId, 10L, 3L, false);

        when(analyticsService.getSurveyReport(eq(surveyId), isNull(), isNull(), anyBoolean())).thenReturn(dto);

        mockMvc.perform(get("/api/admin/reports/form/{id}", surveyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyThresholdMet").value(false));
    }

    // -----------------------------------------------------------------------
    // GET /api/analytics/org-units (AnalyticsController)
    // -----------------------------------------------------------------------

    /**
     * Tests the AnalyticsController.getVisibleOrgUnits endpoint separately
     * since it lives in a different controller. The role-scoping logic is
     * tested in AnalyticsServiceTest; here we just verify the wire-up.
     */
    @Test
    void getVisibleOrgUnits_returnsOrgUnitList() throws Exception {
        AnalyticsController analyticsController = new AnalyticsController(analyticsService);
        MockMvc analyticsMvc = MockMvcBuilders.standaloneSetup(analyticsController).build();

        List<OrgUnitNodeDto> units = List.of(
                new OrgUnitNodeDto(UUID.randomUUID(), "Edge Group", OrgLevel.GROUP, null, 0, "/root"),
                new OrgUnitNodeDto(UUID.randomUUID(), "Backend Team", OrgLevel.TEAM, null, 1, "/root/backend")
        );
        when(analyticsService.getVisibleOrgUnits(isNull())).thenReturn(units);

        analyticsMvc.perform(get("/api/analytics/org-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].orgUnitName").value("Edge Group"))
                .andExpect(jsonPath("$[1].orgLevel").value("TEAM"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SurveyReportDto minimalReport(UUID surveyId, long total, long completed, boolean threshold) {
        return new SurveyReportDto(
                surveyId, "Test Survey",
                total, total,
                completed, 0L,
                total > 0 ? (double) completed / total * 100 : 0.0,
                threshold, List.of(), 0L, completed,
                List.of());
    }
}
