package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SafReconDirectorySyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminDirectorySyncControllerTest {

    SafReconDirectorySyncService syncService;
    AuditService auditService;
    MockMvc mvc;
    UUID caller = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        syncService = mock(SafReconDirectorySyncService.class);
        auditService = mock(AuditService.class);
        var controller = new AdminDirectorySyncController(syncService, auditService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void triggerFullSync_returnsResult() throws Exception {
        when(syncService.fullSync()).thenReturn(
                new DirectorySyncResultDto(10, 3, 7, 0, 4, 1, 3, 0, LocalDateTime.now()));

        mvc.perform(post("/api/admin/saf/sync")
                        .principal(new UsernamePasswordAuthenticationToken(caller, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usersProcessed").value(10))
                .andExpect(jsonPath("$.orgUnitsProcessed").value(4));

        verify(auditService).logAction(eq(caller), eq("SAF_RECON_FULL_SYNC"), eq("SAF_RECON_SYNC"),
                any(), any(), any());
    }
}
