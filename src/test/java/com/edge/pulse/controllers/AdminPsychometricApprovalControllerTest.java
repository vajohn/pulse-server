package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.TestApprovalRequestDto;
import com.edge.pulse.data.enums.TestApprovalStatus;
import com.edge.pulse.mappers.psychometric.TestApprovalMapper;
import com.edge.pulse.services.psychometric.CadenceAdminService;
import com.edge.pulse.services.psychometric.InstrumentService;
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import com.edge.pulse.services.psychometric.TestApprovalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminPsychometricApprovalControllerTest {

    MockMvc mvc;
    PsychometricAdminService adminService = mock(PsychometricAdminService.class);
    CadenceAdminService cadenceAdminService = mock(CadenceAdminService.class);
    InstrumentService instrumentService = mock(InstrumentService.class);
    TestApprovalService approvalService = mock(TestApprovalService.class);
    TestApprovalMapper approvalMapper = mock(TestApprovalMapper.class);

    final ObjectMapper om = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new AdminPsychometricController(
                        adminService, cadenceAdminService, instrumentService,
                        approvalService, approvalMapper))
                .build();
    }

    // Helper to build a minimal PsychometricTestDto
    private PsychometricTestDto testDto(UUID testId, String status) {
        return new PsychometricTestDto(testId, UUID.randomUUID(), "Test One",
                null, null, "PERSONALITY", null, status, 1, LocalDateTime.now(),
                0, 0, null, null, null, null);
    }

    // Helper to build a minimal TestApprovalRequestDto
    private TestApprovalRequestDto approvalDto(UUID requestId, UUID testId, String status) {
        return new TestApprovalRequestDto(requestId, testId, "Test One", 1,
                UUID.randomUUID(), "Sub One", LocalDateTime.now(),
                status, null, null, null, null, null);
    }

    @Test
    void submit_returns200WithPendingStatus() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        PsychometricTestDto dto = testDto(testId, "PENDING_APPROVAL");

        when(approvalService.submitAndGetDto(eq(testId), eq(adminId))).thenReturn(dto);

        mvc.perform(post("/api/admin/psychometric/tests/{testId}/submit", testId)
                        .principal(new UsernamePasswordAuthenticationToken(adminId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void getApprovals_returns200List() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        TestApprovalRequestDto dto = approvalDto(requestId, testId, "PENDING");

        when(approvalService.listDtos(TestApprovalStatus.PENDING)).thenReturn(List.of(dto));

        mvc.perform(get("/api/admin/psychometric/approvals")
                        .param("status", "PENDING")
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void review_approve_returns200() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        TestApprovalRequestDto dto = approvalDto(requestId, testId, "APPROVED");

        when(approvalService.reviewAndGetDto(eq(adminId), eq(requestId), any())).thenReturn(dto);

        String body = om.writeValueAsString(Map.of("decision", "APPROVE", "approvalReference", "email 2026-06-26"));

        mvc.perform(post("/api/admin/psychometric/approvals/{requestId}/review", requestId)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .principal(new UsernamePasswordAuthenticationToken(adminId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void revise_returns200WithDraftStatus() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID testId = UUID.randomUUID();
        PsychometricTestDto dto = testDto(UUID.randomUUID(), "DRAFT");

        when(approvalService.reviseAndGetDto(eq(testId), eq(adminId))).thenReturn(dto);

        mvc.perform(post("/api/admin/psychometric/tests/{testId}/revise", testId)
                        .principal(new UsernamePasswordAuthenticationToken(adminId, null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void activateEndpoint_doesNotExist() throws Exception {
        // The /activate endpoint should have been removed (replaced by submit/approve)
        mvc.perform(post("/api/admin/psychometric/tests/{testId}/activate", UUID.randomUUID())
                        .principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, List.of())))
                .andExpect(status().isNotFound());
    }
}
