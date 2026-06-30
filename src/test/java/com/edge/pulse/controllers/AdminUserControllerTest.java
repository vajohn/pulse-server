package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.UserFilter;
import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.mappers.RoleChangeMapper;
import com.edge.pulse.services.OrgUnitScopeService;
import com.edge.pulse.services.RoleChangeService;
import com.edge.pulse.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@code GET /api/admin/users} filter binding (feat/user-filters).
 * Verifies repeated roleNames bind to a List and all filter params reach UserService.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    private MockMvc mockMvc;

    @Mock private UserService userService;
    @Mock private OrgUnitScopeService scopeService;
    @Mock private RoleChangeService roleChangeService;
    @Mock private RoleChangeMapper roleChangeMapper;

    @Captor private ArgumentCaptor<UserFilter> filterCaptor;

    private static final UUID AUTH_USER_ID = UUID.randomUUID();
    private static final Page<UserSummary> EMPTY_PAGE =
            new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
    private UsernamePasswordAuthenticationToken principal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminUserController(userService, scopeService, roleChangeService, roleChangeMapper)).build();
        principal = new UsernamePasswordAuthenticationToken(AUTH_USER_ID, null, List.of());
    }

    @Test
    void getUsers_bindsFiltersAndReachesService() throws Exception {
        when(userService.getUsersPage(any(), eq(AUTH_USER_ID), any(UserFilter.class), any(Pageable.class)))
                .thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/users")
                        .param("roleNames", "A")
                        .param("roleNames", "B")
                        .param("permission", "X")
                        .param("status", "active")
                        .principal(principal))
                .andExpect(status().isOk());

        verify(userService).getUsersPage(any(), eq(AUTH_USER_ID), filterCaptor.capture(), any(Pageable.class));
        UserFilter f = filterCaptor.getValue();
        assertThat(f.roleNames()).containsExactly("A", "B");
        assertThat(f.permission()).isEqualTo("X");
        assertThat(f.status()).isEqualTo("active");
        assertThat(f.noRoles()).isFalse();
        assertThat(f.neverLoggedIn()).isFalse();
    }

    @Test
    void getUsers_noFilterParams_buildsEmptyish() throws Exception {
        when(userService.getUsersPage(any(), eq(AUTH_USER_ID), any(UserFilter.class), any(Pageable.class)))
                .thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/users").principal(principal))
                .andExpect(status().isOk());

        verify(userService).getUsersPage(any(), eq(AUTH_USER_ID), filterCaptor.capture(), any(Pageable.class));
        UserFilter f = filterCaptor.getValue();
        assertThat(f.roleNames()).isEmpty();
        assertThat(f.q()).isNull();
        assertThat(f.permission()).isNull();
        assertThat(f.noRoles()).isFalse();
        assertThat(f.staleDays()).isNull();
    }

    @Test
    void getUsers_booleanAndIntParams_bind() throws Exception {
        when(userService.getUsersPage(any(), eq(AUTH_USER_ID), any(UserFilter.class), any(Pageable.class)))
                .thenReturn(EMPTY_PAGE);

        mockMvc.perform(get("/api/admin/users")
                        .param("noRoles", "true")
                        .param("neverLoggedIn", "true")
                        .param("staleDays", "30")
                        .param("syncSource", "SAF")
                        .principal(principal))
                .andExpect(status().isOk());

        verify(userService).getUsersPage(any(), eq(AUTH_USER_ID), filterCaptor.capture(), any(Pageable.class));
        UserFilter f = filterCaptor.getValue();
        assertThat(f.noRoles()).isTrue();
        assertThat(f.neverLoggedIn()).isTrue();
        assertThat(f.staleDays()).isEqualTo(30);
        assertThat(f.syncSource()).isEqualTo("SAF");
    }
}
