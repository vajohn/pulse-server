package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AdminReportSummary;
import com.edge.pulse.services.AnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies Phase 2 change: AdminReportController.getSummary() now delegates
 * to AnalyticsService.getAdminReportSummary() instead of hitting 4 repos directly.
 */
@ExtendWith(MockitoExtension.class)
class AdminReportSummaryTest {

    private MockMvc mockMvc;
    @Mock private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReportController(analyticsService)).build();
    }

    @Test
    void getSummary_delegatesToAnalyticsService() throws Exception {
        AdminReportSummary summary = new AdminReportSummary(100L, 90L, 5L, 20L, 150L, 3L);
        when(analyticsService.getAdminReportSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(100))
                .andExpect(jsonPath("$.activeUsers").value(90))
                .andExpect(jsonPath("$.totalSurveys").value(5))
                .andExpect(jsonPath("$.totalAssignments").value(20))
                .andExpect(jsonPath("$.completedSessions").value(150))
                .andExpect(jsonPath("$.pendingApprovals").value(3));

        verify(analyticsService).getAdminReportSummary();
    }
}
