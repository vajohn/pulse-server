package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CheckInDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.enums.Cadence;
import com.edge.pulse.services.psychometric.micro.MicroEngagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MicroEngagementControllerTest {

    MockMvc mvc;
    MicroEngagementService service = mock(MicroEngagementService.class);

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new MicroEngagementController(service)).build();
    }

    @Test
    void listsCheckIns() throws Exception {
        UUID userId = UUID.randomUUID();
        CheckInDto dto = new CheckInDto(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Resilience Pulse", Cadence.WEEKLY, 3, 1, 2);
        when(service.listCheckIns(any())).thenReturn(List.of(dto));

        mvc.perform(get("/api/psychometric/check-ins")
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].testName").value("Resilience Pulse"))
                .andExpect(jsonPath("$[0].scalesConsolidated").value(1))
                .andExpect(jsonPath("$[0].scalesTotal").value(2));
    }

    @Test
    void startsCheckInSession() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID cadenceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        PsychometricSessionDto session = new PsychometricSessionDto(
                sessionId, "Resilience Pulse", "PERSONALITY", null, null, null, 1L,
                List.of(), List.of());
        when(service.buildCheckInSession(eq(cadenceId), any())).thenReturn(session);

        mvc.perform(post("/api/psychometric/check-ins/{cadenceId}/session", cadenceId)
                        .principal(new UsernamePasswordAuthenticationToken(userId, null, List.of())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));
    }
}
