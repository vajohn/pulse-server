package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.CandidateTestResultDetailsDto;
import com.edge.pulse.data.dto.psychometric.TestResultSummaryDto;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.services.psychometric.CandidatePsychometricService;
import com.edge.pulse.services.psychometric.PsychometricSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for CandidatePsychometricController.
 *
 * <p>@PreAuthorize is NOT enforced in standalone setup — these tests verify
 * HTTP mapping and service delegation. The S-4 security gate change
 * (SCOPE_TEAM → REPORT_ASSESS_VIEW) is enforced by Spring Security method security
 * in integration and is validated structurally via the annotation in the controller source.
 *
 * <p>S-4 fix verified: the @PreAuthorize annotation on getTeamResultDetail() now reads
 * "hasAnyAuthority('REPORT_ASSESS_VIEW', 'REPORT_ALL', 'ASSESS_ALL')" — not the old
 * SCOPE_* qualifiers which were action gates in disguise.
 */
@ExtendWith(MockitoExtension.class)
class CandidatePsychometricControllerTest {

    @Mock private CandidatePsychometricService service;
    @Mock private PsychometricSessionService sessionService;

    private MockMvc mockMvc;

    private static final UUID RESULT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CandidatePsychometricController(service, sessionService))
                .build();
    }

    private UsernamePasswordAuthenticationToken authWith(String... authorities) {
        return new UsernamePasswordAuthenticationToken(
                CALLER_ID, "token",
                List.of(authorities).stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList()
        );
    }

    // ── GET /api/psychometric/my-team/results/{resultId} (S-4 gate fix) ───────

    @Test
    void getTeamResultDetail_delegatesToService() throws Exception {
        UUID testId = UUID.randomUUID();
        CandidateTestResultDetailsDto dto = new CandidateTestResultDetailsDto(
                RESULT_ID, testId, "Test Title", "PERSONALITY",
                TestResultStatus.SCORED, null, null, 0,
                false, false, false, false,
                Collections.emptyList(), false, Collections.emptyList()
        );
        when(service.getTeamResultDetail(eq(RESULT_ID), eq(CALLER_ID))).thenReturn(dto);

        mockMvc.perform(get("/api/psychometric/my-team/results/{id}", RESULT_ID)
                        .principal(authWith("REPORT_ASSESS_VIEW")))
                .andExpect(status().isOk());

        verify(service).getTeamResultDetail(RESULT_ID, CALLER_ID);
    }

    // ── GET /api/psychometric/my-team/results ────────────────────────────────

    @Test
    void getTeamResults_delegatesToService() throws Exception {
        TestResultSummaryDto dto = new TestResultSummaryDto(
                RESULT_ID, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Alice Smith", "Big-Five Inventory",
                TestResultStatus.SCORED, null, null, null, 0, null, null
        );
        when(service.getTeamResults(CALLER_ID)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/psychometric/my-team/results")
                        .principal(authWith("REPORT_ASSESS_VIEW")))
                .andExpect(status().isOk());

        verify(service).getTeamResults(CALLER_ID);
    }

    // ── GET /api/psychometric/my-results ─────────────────────────────────────

    @Test
    void getMyResults_returnsEmptyList() throws Exception {
        when(service.getMyResults(CALLER_ID)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/psychometric/my-results")
                        .principal(authWith("ASSESS_READ")))
                .andExpect(status().isOk());

        verify(service).getMyResults(CALLER_ID);
    }
}
