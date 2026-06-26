package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CadenceConfigDto;
import com.edge.pulse.data.enums.Cadence;
import com.edge.pulse.services.psychometric.CadenceAdminService;
import com.edge.pulse.services.psychometric.InstrumentService;
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPsychometricCadenceTest {

    MockMvc mvc;
    PsychometricAdminService adminService = mock(PsychometricAdminService.class);
    CadenceAdminService cadenceAdminService = mock(CadenceAdminService.class);
    InstrumentService instrumentService = mock(InstrumentService.class);
    com.edge.pulse.services.psychometric.TestApprovalService approvalService =
            mock(com.edge.pulse.services.psychometric.TestApprovalService.class);
    com.edge.pulse.mappers.psychometric.TestApprovalMapper approvalMapper =
            mock(com.edge.pulse.mappers.psychometric.TestApprovalMapper.class);
    final ObjectMapper om = new ObjectMapper();

    private UUID testId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminPsychometricController(
                        adminService, cadenceAdminService, instrumentService,
                        approvalService, approvalMapper))
                .build();
        testId = UUID.randomUUID();
    }

    @Test
    void createCadence_validBody_returns201() throws Exception {
        UUID adminId = UUID.randomUUID();
        CadenceConfigDto dto = new CadenceConfigDto(UUID.randomUUID(), testId, Cadence.WEEKLY, 12,
                null, true, null, null, true);
        when(cadenceAdminService.create(eq(testId), any(), any())).thenReturn(dto);

        String body = om.writeValueAsString(Map.of(
                "cadence", "WEEKLY", "maxItemsPerAdmin", 12, "includeChildren", true));

        mvc.perform(post("/api/admin/psychometric/tests/{testId}/cadences", testId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .principal(new UsernamePasswordAuthenticationToken(adminId, null, List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cadence").value("WEEKLY"))
                .andExpect(jsonPath("$.maxItemsPerAdmin").value(12));
    }

    @Test
    void listCadences_returnsList() throws Exception {
        CadenceConfigDto dto = new CadenceConfigDto(UUID.randomUUID(), testId, Cadence.MONTHLY, 8,
                null, false, null, null, true);
        when(cadenceAdminService.list(testId)).thenReturn(List.of(dto));

        mvc.perform(get("/api/admin/psychometric/tests/{testId}/cadences", testId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cadence").value("MONTHLY"))
                .andExpect(jsonPath("$[0].maxItemsPerAdmin").value(8));
    }

    @Test
    void deleteCadence_returns204() throws Exception {
        UUID cadenceId = UUID.randomUUID();
        mvc.perform(delete("/api/admin/psychometric/tests/{testId}/cadences/{cadenceId}", testId, cadenceId))
                .andExpect(status().isNoContent());
        verify(cadenceAdminService).deactivate(testId, cadenceId);
    }

    @Test
    void createCadence_maxItemsOutOfRange_returns400() throws Exception {
        UUID adminId = UUID.randomUUID();
        String body = om.writeValueAsString(Map.of(
                "cadence", "WEEKLY", "maxItemsPerAdmin", 20, "includeChildren", true));

        mvc.perform(post("/api/admin/psychometric/tests/{testId}/cadences", testId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .principal(new UsernamePasswordAuthenticationToken(adminId, null, List.of())))
                .andExpect(status().isBadRequest());
        verify(cadenceAdminService, never()).create(any(), any(), any());
    }
}
