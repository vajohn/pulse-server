package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AuditLogDto;
import com.edge.pulse.services.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies Phase 2 change: AdminAuditController delegates all query logic
 * (including size clamping) to AuditService.getAuditLogs().
 */
@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    private MockMvc mockMvc;
    @Mock private AuditService auditService;

    private static final Page<AuditLogDto> EMPTY_PAGE =
            new PageImpl<>(List.of(), PageRequest.of(0, 50), 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAuditController(auditService)).build();
    }

    @Test
    void getAuditLogs_noParams_delegatesToService() throws Exception {
        when(auditService.getAuditLogs(isNull(), isNull(), eq(0), eq(50))).thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isOk());

        verify(auditService).getAuditLogs(isNull(), isNull(), eq(0), eq(50));
    }

    @Test
    void getAuditLogs_withUserId_passesUserIdToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(auditService.getAuditLogs(eq(userId), isNull(), eq(0), eq(50))).thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/audit-logs").param("userId", userId.toString()))
                .andExpect(status().isOk());

        verify(auditService).getAuditLogs(eq(userId), isNull(), eq(0), eq(50));
    }

    @Test
    void getAuditLogs_withEntityType_passesEntityTypeToService() throws Exception {
        when(auditService.getAuditLogs(isNull(), eq("SURVEY"), eq(0), eq(50))).thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/audit-logs").param("entityType", "SURVEY"))
                .andExpect(status().isOk());

        verify(auditService).getAuditLogs(isNull(), eq("SURVEY"), eq(0), eq(50));
    }

    @Test
    void getAuditLogs_withBothFilters_passesBothToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(auditService.getAuditLogs(eq(userId), eq("USER"), eq(1), eq(25))).thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("userId", userId.toString())
                        .param("entityType", "USER")
                        .param("page", "1")
                        .param("size", "25"))
                .andExpect(status().isOk());

        verify(auditService).getAuditLogs(eq(userId), eq("USER"), eq(1), eq(25));
    }
}
