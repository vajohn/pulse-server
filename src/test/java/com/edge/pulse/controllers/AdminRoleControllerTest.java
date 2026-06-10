package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AdminRoleDto;
import com.edge.pulse.data.dto.AssignableRoleDto;
import com.edge.pulse.data.dto.PermissionDto;
import com.edge.pulse.services.RoleManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MockMvc tests for AdminRoleController.
 *
 * <p>@PreAuthorize is NOT enforced in standalone setup — these tests focus on
 * request/response mapping, service delegation, and HTTP status codes.
 * The privilege escalation and SCOPE guards are tested at the service level via
 * the {@code request} body + {@code auth} parameter passed through to RoleManagementService.
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    @Mock private RoleManagementService roleManagementService;

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ROLE_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminRoleController(roleManagementService))
                .build();
    }

    // ── GET /api/admin/roles ─────────────────────────────────────────────────

    @Test
    void listRoles_returnsListFromService() throws Exception {
        AdminRoleDto dto = new AdminRoleDto(ROLE_ID, "HR_FULL_CRUD",
                List.of(new PermissionDto("USR_READ", "View users", "USR")), 3);
        when(roleManagementService.listRoles()).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/admin/roles").principal(actorAuth("ROLE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("HR_FULL_CRUD"))
                .andExpect(jsonPath("$[0].userCount").value(3))
                .andExpect(jsonPath("$[0].permissions[0].name").value("USR_READ"));

        verify(roleManagementService).listRoles();
    }

    @Test
    void listRoles_emptyList_returns200WithEmptyArray() throws Exception {
        when(roleManagementService.listRoles()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/roles").principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── POST /api/admin/roles ────────────────────────────────────────────────

    @Test
    void createRole_validRequest_returns201WithDto() throws Exception {
        AdminRoleDto created = new AdminRoleDto(ROLE_ID, "CUSTOM_ROLE", List.of(), 0);
        when(roleManagementService.createRole(any(), eq(ACTOR_ID))).thenReturn(created);

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CUSTOM_ROLE"}
                                """)
                        .principal(actorAuth("ROLE_CREATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("CUSTOM_ROLE"))
                .andExpect(jsonPath("$.userCount").value(0));

        verify(roleManagementService).createRole(any(), eq(ACTOR_ID));
    }

    @Test
    void createRole_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":""}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRole_duplicateRole_servicePropagatesConflict() throws Exception {
        when(roleManagementService.createRole(any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Role already exists"));

        mockMvc.perform(post("/api/admin/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"HR_FULL_CRUD"}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isConflict());
    }

    // ── PUT /api/admin/roles/{id}/permissions ────────────────────────────────

    @Test
    void setPermissions_validRequest_returns200WithUpdatedDto() throws Exception {
        AdminRoleDto updated = new AdminRoleDto(ROLE_ID, "HR_FULL_CRUD",
                List.of(new PermissionDto("USR_READ", "View users", "USR"),
                        new PermissionDto("FORM_READ", "View forms", "FORM")),
                2);
        when(roleManagementService.setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":["USR_READ","FORM_READ"]}
                                """)
                        .principal(actorAuth("ROLE_UPDATE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.length()").value(2));

        verify(roleManagementService).setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID));
    }

    @Test
    void setPermissions_emptySet_clearsAllPermissions() throws Exception {
        AdminRoleDto cleared = new AdminRoleDto(ROLE_ID, "HR_FULL_CRUD", List.of(), 0);
        when(roleManagementService.setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID)))
                .thenReturn(cleared);

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":[]}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void setPermissions_nullPermissions_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setPermissions_privilegeEscalationBlocked_servicePropagates403() throws Exception {
        when(roleManagementService.setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Cannot grant permissions you do not hold: [ROLE_ALL]"));

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":["ROLE_ALL"]}
                                """)
                        .principal(actorAuth("ROLE_UPDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void setPermissions_scopePermissionWithoutRoleAll_servicePropagates403() throws Exception {
        when(roleManagementService.setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "SCOPE permissions require ROLE_ALL authority to grant: [SCOPE_ORG_WIDE]"));

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":["SCOPE_ORG_WIDE"]}
                                """)
                        .principal(actorAuth("ROLE_UPDATE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void setPermissions_unknownPermission_servicePropagates400() throws Exception {
        when(roleManagementService.setPermissions(eq(ROLE_ID), any(), any(), eq(ACTOR_ID)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown permission: MADE_UP"));

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", ROLE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":["MADE_UP"]}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setPermissions_roleNotFound_servicePropagates404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(roleManagementService.setPermissions(eq(unknownId), any(), any(), eq(ACTOR_ID)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));

        mockMvc.perform(put("/api/admin/roles/{id}/permissions", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissions":["USR_READ"]}
                                """)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/admin/roles/{id} ─────────────────────────────────────────

    @Test
    void deleteRole_existingRoleNoUsers_returns204() throws Exception {
        doNothing().when(roleManagementService).deleteRole(eq(ROLE_ID), eq(ACTOR_ID));

        mockMvc.perform(delete("/api/admin/roles/{id}", ROLE_ID)
                        .principal(actorAuth("ROLE_DELETE")))
                .andExpect(status().isNoContent());

        verify(roleManagementService).deleteRole(eq(ROLE_ID), eq(ACTOR_ID));
    }

    @Test
    void deleteRole_roleHasAssignedUsers_servicePropagates409() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "Role has 3 assigned user(s) and cannot be deleted"))
                .when(roleManagementService).deleteRole(eq(ROLE_ID), eq(ACTOR_ID));

        mockMvc.perform(delete("/api/admin/roles/{id}", ROLE_ID)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteRole_roleNotFound_servicePropagates404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"))
                .when(roleManagementService).deleteRole(eq(unknownId), eq(ACTOR_ID));

        mockMvc.perform(delete("/api/admin/roles/{id}", unknownId)
                        .principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/admin/roles/assignable ─────────────────────────────────────

    @Test
    void listAssignableRoles_returnsIdAndNameOnly() throws Exception {
        List<AssignableRoleDto> dtos = List.of(
                new AssignableRoleDto(ROLE_ID, "HR_FULL_CRUD"),
                new AssignableRoleDto(UUID.randomUUID(), "SURVEY_RESPONDENT"));
        when(roleManagementService.listAssignableRoles()).thenReturn(dtos);

        mockMvc.perform(get("/api/admin/roles/assignable").principal(actorAuth("USR_ROLE_ASSIGN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("HR_FULL_CRUD"))
                .andExpect(jsonPath("$[0].id").value(ROLE_ID.toString()))
                // Must NOT expose permissions or userCount
                .andExpect(jsonPath("$[0].permissions").doesNotExist())
                .andExpect(jsonPath("$[0].userCount").doesNotExist());

        verify(roleManagementService).listAssignableRoles();
    }

    @Test
    void listAssignableRoles_emptyList_returns200WithEmptyArray() throws Exception {
        when(roleManagementService.listAssignableRoles()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/roles/assignable").principal(actorAuth("USR_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/admin/roles/permissions ────────────────────────────────────

    @Test
    void listAllPermissions_returnsPermissionsWithGrouping() throws Exception {
        List<PermissionDto> perms = List.of(
                new PermissionDto("FORM_READ", "View forms", "FORM"),
                new PermissionDto("USR_READ", "View users", "USR"));
        when(roleManagementService.listAllPermissions()).thenReturn(perms);

        mockMvc.perform(get("/api/admin/roles/permissions").principal(actorAuth("ROLE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("FORM_READ"))
                .andExpect(jsonPath("$[0].description").value("View forms"))
                .andExpect(jsonPath("$[0].group").value("FORM"));

        verify(roleManagementService).listAllPermissions();
    }

    @Test
    void listAllPermissions_emptyList_returns200WithEmptyArray() throws Exception {
        when(roleManagementService.listAllPermissions()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/roles/permissions").principal(actorAuth("ROLE_ALL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UsernamePasswordAuthenticationToken actorAuth(String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(ACTOR_ID, null, grantedAuthorities);
    }
}
