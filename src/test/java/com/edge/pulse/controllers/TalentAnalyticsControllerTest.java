package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CapabilityTrendDto;
import com.edge.pulse.data.dto.psychometric.CohortAnalyticsDto;
import com.edge.pulse.services.psychometric.TalentAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TalentAnalyticsControllerTest {

    MockMvc mvc;
    TalentAnalyticsService service = mock(TalentAnalyticsService.class);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TalentAnalyticsController(service)).build();
    }

    @Test
    void cohort_returnsOk_andPassesPrincipalAsCaller() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        when(service.cohortAnalytics(eq(testId), eq(userId)))
                .thenReturn(new CohortAnalyticsDto(testId, false, 6, List.of()));

        mvc.perform(get("/api/admin/psychometric/analytics/tests/{testId}/cohort", testId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masked").value(false))
                .andExpect(jsonPath("$.subjectCount").value(6));
    }

    @Test
    void trend_returnsOk() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        when(service.capabilityTrend(eq(userId), eq(subjectId), eq(testId)))
                .thenReturn(new CapabilityTrendDto(subjectId, testId, List.of()));

        mvc.perform(get("/api/admin/psychometric/analytics/tests/{testId}/users/{userId}/trend",
                        testId, subjectId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testId").value(testId.toString()));
    }
}
