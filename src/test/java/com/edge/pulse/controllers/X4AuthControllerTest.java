package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.RoleRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.JwtTokenService;
import com.edge.pulse.services.PermissionCacheService;
import com.edge.pulse.services.X4AuthService;
import com.edge.pulse.services.X4AuthService.InitiateResult;
import com.edge.pulse.services.X4AuthService.PollResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * L1 verification (DB-free) for the additive X4Auth broker.
 *
 * <p>Proves the Pulse-side correctness of the air-gapped login replacement:
 * an approved X4Auth identity yields Pulse JWTs, an unknown email is auto-provisioned
 * as EMPLOYEE, and a not-approved transaction is rejected with 401 — all without
 * touching the live X4Auth server, the database, or the existing Azure flow.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class X4AuthControllerTest {

    private MockMvc mockMvc;

    @Mock private X4AuthService x4AuthService;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private PermissionCacheService permissionCacheService;
    @Mock private AuditService auditService;

    private static final UserSummary SUMMARY = new UserSummary(
            UUID.randomUUID(), "jane@edge.ae", "Jane Doe", "Ops",
            List.of("EMPLOYEE"), List.of(), null, null);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new X4AuthController(
                x4AuthService, userRepository, roleRepository,
                jwtTokenService, permissionCacheService, auditService)).build();

        when(jwtTokenService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtTokenService.generateRefreshToken(any(User.class), any(), any())).thenReturn("refresh-token");
        when(permissionCacheService.toUserSummary(any(User.class))).thenReturn(SUMMARY);
        when(auditService.buildDetail(any(), any(), any(), any())).thenReturn("{}");
    }

    @Test
    void config_reflectsServiceConfiguredState() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);

        mockMvc.perform(get("/api/auth/x4auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void initiate_success_returnsTransactionIdAndVerificationCode() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);
        when(x4AuthService.initiate(eq("jane@edge.ae")))
                .thenReturn(new InitiateResult(true, "txn-123", 2000, "4821", null, null));

        mockMvc.perform(post("/api/auth/x4auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jane@edge.ae\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("txn-123"))
                .andExpect(jsonPath("$.verificationCode").value("4821"));
    }

    @Test
    void initiate_userNotFound_returns400WithErrorCode() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);
        when(x4AuthService.initiate(anyString()))
                .thenReturn(new InitiateResult(false, null, null, null, "USER_NOT_FOUND", "No enterprise account"));

        mockMvc.perform(post("/api/auth/x4auth/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@edge.ae\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void status_returnsStatusAndApprovedFlag() throws Exception {
        when(x4AuthService.poll("txn-123"))
                .thenReturn(new PollResult("approved", "jane@edge.ae", "Jane", "Ops", null));

        mockMvc.perform(get("/api/auth/x4auth/status/txn-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.isApproved").value(true));
    }

    @Test
    void complete_approvedExistingUser_issuesPulseTokens() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);
        when(x4AuthService.consumeApproved("txn-123"))
                .thenReturn(new PollResult("approved", "jane@edge.ae", "Jane Doe", "Ops", null));
        User existing = User.builder().id(UUID.randomUUID()).email("jane@edge.ae").displayName("Jane Doe").build();
        when(userRepository.findByEmail("jane@edge.ae")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/auth/x4auth/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"txn-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user.email").value("jane@edge.ae"));

        verify(roleRepository, never()).findByName(anyString()); // existing user → no provisioning
    }

    @Test
    void complete_approvedUnknownUser_autoProvisionsAsEmployee() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);
        when(x4AuthService.consumeApproved("txn-new"))
                .thenReturn(new PollResult("approved", "newhire@edge.ae", "New Hire", "Field", null));
        when(userRepository.findByEmail("newhire@edge.ae")).thenReturn(Optional.empty());
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(mock(Role.class)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return User.builder().id(UUID.randomUUID()).email(u.getEmail())
                    .displayName(u.getDisplayName()).department(u.getDepartment())
                    .roles(u.getRoles()).build();
        });

        mockMvc.perform(post("/api/auth/x4auth/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"txn-new\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));

        verify(roleRepository).findByName("EMPLOYEE");          // provisioned with default role
        verify(userRepository).save(any(User.class));
        verify(auditService).logAction(any(UUID.class), eq("USER_AUTOPROVISION_X4AUTH"),
                eq("user"), any(UUID.class), anyString(), any());
    }

    @Test
    void complete_notApproved_returns401() throws Exception {
        when(x4AuthService.isConfigured()).thenReturn(true);
        when(x4AuthService.consumeApproved("txn-pending")).thenReturn(null);

        mockMvc.perform(post("/api/auth/x4auth/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionId\":\"txn-pending\"}"))
                .andExpect(status().isUnauthorized());

        verify(jwtTokenService, never()).generateAccessToken(any());
    }
}
