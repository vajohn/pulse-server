package com.edge.pulse.services;

import com.edge.pulse.data.dto.UpdateUserRequest;
import com.edge.pulse.data.dto.UserFilter;
import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.RoleRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Phase 2 change: business logic extracted from AdminUserController
 * and UserController into UserService.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private OrganizationalUnitRepository orgUnitRepository;
    @Mock private OrgUnitScopeService scopeService;
    @Mock private PermissionCacheService permissionCacheService;
    @Mock private AuditService auditService;
    @Mock private FormCacheService formCacheService;

    private UserService userService;

    private static final UUID AUTH_USER_ID = UUID.randomUUID();
    private static final UUID TARGET_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, orgUnitRepository,
                scopeService, permissionCacheService, auditService, formCacheService);
    }

    // -----------------------------------------------------------------------
    // getUsers
    // -----------------------------------------------------------------------

    @Test
    void getUsers_noOrgUnit_returnsAllScopedUsers() {
        User u1 = User.builder().id(UUID.randomUUID()).build();
        User u2 = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));
        when(scopeService.filterByScope(eq(AUTH_USER_ID), anyList())).thenReturn(List.of(u1));
        UserSummary summary = new UserSummary(u1.getId(), "a@b.com", "Alice", null,
                List.of(), List.of(), null, null);
        when(permissionCacheService.toUserSummary(u1)).thenReturn(summary);

        List<UserSummary> result = userService.getUsers(null, AUTH_USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(summary);
        verify(userRepository).findAll();
        verify(scopeService).filterByScope(eq(AUTH_USER_ID), anyList());
    }

    @Test
    void getUsers_withOrgUnit_queriesByOrgUnitId() {
        UUID orgUnitId = UUID.randomUUID();
        when(userRepository.findByOrgUnitId(orgUnitId)).thenReturn(List.of());
        when(scopeService.filterByScope(AUTH_USER_ID, List.of())).thenReturn(List.of());

        List<UserSummary> result = userService.getUsers(orgUnitId, AUTH_USER_ID);

        assertThat(result).isEmpty();
        verify(userRepository).findByOrgUnitId(orgUnitId);
        verify(userRepository, never()).findAll();
    }

    // -----------------------------------------------------------------------
    // getUserSummary
    // -----------------------------------------------------------------------

    @Test
    void getUserSummary_found_returnsMapped() {
        User user = User.builder().id(TARGET_USER_ID).build();
        UserSummary summary = new UserSummary(TARGET_USER_ID, "x@y.com", "X", null,
                List.of(), List.of(), null, null);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        assertThat(userService.getUserSummary(TARGET_USER_ID)).isEqualTo(summary);
    }

    @Test
    void getUserSummary_notFound_throws() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.getUserSummary(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // -----------------------------------------------------------------------
    // updateUser — scope check
    // -----------------------------------------------------------------------

    @Test
    void updateUser_outsideScope_throwsAccessDenied() {
        when(scopeService.canAccess(AUTH_USER_ID, TARGET_USER_ID)).thenReturn(false);

        UpdateUserRequest req = new UpdateUserRequest("NewName", null, null, null, null);
        assertThatThrownBy(() -> userService.updateUser(TARGET_USER_ID, req, AUTH_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(userRepository, never()).findById(any());
        verify(auditService, never()).logAction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateUser_withinScope_mutatesAndAudits() {
        User user = User.builder().id(TARGET_USER_ID).displayName("Old").build();
        when(scopeService.canAccess(AUTH_USER_ID, TARGET_USER_ID)).thenReturn(true);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        UserSummary summary = new UserSummary(TARGET_USER_ID, "x@y.com", "New", null,
                List.of(), List.of(), null, null);
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        UpdateUserRequest req = new UpdateUserRequest("New", "Engineering", null, null, null);
        UserSummary result = userService.updateUser(TARGET_USER_ID, req, AUTH_USER_ID);

        assertThat(result).isEqualTo(summary);
        assertThat(user.getDisplayName()).isEqualTo("New");
        assertThat(user.getDepartment()).isEqualTo("Engineering");
        verify(userRepository).save(user);
        verify(auditService).logAction(eq(AUTH_USER_ID), eq("USER_UPDATE"), eq("USER"),
                eq(TARGET_USER_ID), isNull(), isNull());
    }

    // -----------------------------------------------------------------------
    // assignRoles
    // -----------------------------------------------------------------------

    @Test
    void assignRoles_validRoles_setsAndAudits() {
        User user = User.builder().id(TARGET_USER_ID).roles(new HashSet<>()).build();
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        UserSummary summary = new UserSummary(TARGET_USER_ID, "x@y.com", "X", null,
                List.of("ADMIN"), List.of(), null, null);
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        UserSummary result = userService.assignRoles(TARGET_USER_ID, List.of("ADMIN"), AUTH_USER_ID);

        assertThat(result).isEqualTo(summary);
        assertThat(user.getRoles()).contains(adminRole);
        verify(userRepository).save(user);
        verify(auditService).logAction(eq(AUTH_USER_ID), eq("ROLE_ASSIGN"), eq("USER"),
                eq(TARGET_USER_ID), any(), isNull());
    }

    @Test
    void assignRoles_unknownRole_throws() {
        User user = User.builder().id(TARGET_USER_ID).roles(new HashSet<>()).build();
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignRoles(TARGET_USER_ID, List.of("GHOST"), AUTH_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found: GHOST");
        verify(userRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // moveUserToOrgUnit (4-G)
    // -----------------------------------------------------------------------

    @Test
    void moveUserToOrgUnit_outsideScope_throwsAccessDenied() {
        when(scopeService.canAccess(AUTH_USER_ID, TARGET_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> userService.moveUserToOrgUnit(TARGET_USER_ID, UUID.randomUUID(), AUTH_USER_ID))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void moveUserToOrgUnit_validMove_updatesOrgUnitAndEvictsCache() {
        UUID newOrgUnitId = UUID.randomUUID();
        User user = User.builder().id(TARGET_USER_ID).build();
        OrganizationalUnit ou = OrganizationalUnit.builder().id(newOrgUnitId).build();
        when(scopeService.canAccess(AUTH_USER_ID, TARGET_USER_ID)).thenReturn(true);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        when(orgUnitRepository.findById(newOrgUnitId)).thenReturn(Optional.of(ou));
        UserSummary summary = new UserSummary(TARGET_USER_ID, "x@y.com", "X", null,
                List.of(), List.of(), null, null);
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        UserSummary result = userService.moveUserToOrgUnit(TARGET_USER_ID, newOrgUnitId, AUTH_USER_ID);

        assertThat(result).isEqualTo(summary);
        assertThat(user.getOrgUnit()).isEqualTo(ou);
        verify(userRepository).save(user);
        verify(formCacheService).evict(FormCacheService.userAssignmentsKey(TARGET_USER_ID));
        verify(auditService).logAction(eq(AUTH_USER_ID), eq("USER_MOVE_ORG_UNIT"), eq("USER"),
                eq(TARGET_USER_ID), any(), isNull());
    }

    @Test
    void moveUserToOrgUnit_orgUnitNotFound_throws() {
        UUID newOrgUnitId = UUID.randomUUID();
        User user = User.builder().id(TARGET_USER_ID).build();
        when(scopeService.canAccess(AUTH_USER_ID, TARGET_USER_ID)).thenReturn(true);
        when(userRepository.findById(TARGET_USER_ID)).thenReturn(Optional.of(user));
        when(orgUnitRepository.findById(newOrgUnitId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.moveUserToOrgUnit(TARGET_USER_ID, newOrgUnitId, AUTH_USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Org unit not found");
        verify(userRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // getProfile / updateProfile (UserController delegation)
    // -----------------------------------------------------------------------

    @Test
    void getProfile_returnsUserSummary() {
        User user = User.builder().id(AUTH_USER_ID).build();
        UserSummary summary = new UserSummary(AUTH_USER_ID, "me@x.com", "Me", null,
                List.of(), List.of(), null, null);
        when(userRepository.findById(AUTH_USER_ID)).thenReturn(Optional.of(user));
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        assertThat(userService.getProfile(AUTH_USER_ID)).isEqualTo(summary);
    }

    @Test
    void updateProfile_updatesDisplayNameAndDepartment() {
        User user = User.builder().id(AUTH_USER_ID).displayName("Old").build();
        UserSummary summary = new UserSummary(AUTH_USER_ID, "me@x.com", "New", "IT",
                List.of(), List.of(), null, null);
        when(userRepository.findById(AUTH_USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(permissionCacheService.toUserSummary(user)).thenReturn(summary);

        UserSummary result = userService.updateProfile(AUTH_USER_ID, "New", "IT");

        assertThat(result).isEqualTo(summary);
        assertThat(user.getDisplayName()).isEqualTo("New");
        assertThat(user.getDepartment()).isEqualTo("IT");
    }

    @Test
    void updateProfile_nullFields_areNotOverwritten() {
        User user = User.builder().id(AUTH_USER_ID).displayName("Keep").department("Keep").build();
        when(userRepository.findById(AUTH_USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(permissionCacheService.toUserSummary(user)).thenReturn(
                new UserSummary(AUTH_USER_ID, null, "Keep", "Keep", List.of(), List.of(), null, null));

        userService.updateProfile(AUTH_USER_ID, null, null);

        assertThat(user.getDisplayName()).isEqualTo("Keep");
        assertThat(user.getDepartment()).isEqualTo("Keep");
    }

    // -----------------------------------------------------------------------
    // getUsersPage (2-D: paginated user list)
    // -----------------------------------------------------------------------

    @Test
    void getUsersPage_paginatesCorrectly() {
        User u1 = User.builder().id(UUID.randomUUID()).build();
        User u2 = User.builder().id(UUID.randomUUID()).build();
        User u3 = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findAll()).thenReturn(List.of(u1, u2, u3));
        when(scopeService.filterByScope(eq(AUTH_USER_ID), anyList())).thenReturn(List.of(u1, u2, u3));
        when(permissionCacheService.toUserSummary(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserSummary(u.getId(), null, null, null, List.of(), List.of(), null, null);
        });

        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // searchUsers (2-N: employee search with input limit)
    // -----------------------------------------------------------------------

    @Test
    void searchUsers_delegatesToRepository() {
        User u1 = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase("alice", "alice"))
                .thenReturn(List.of(u1));
        UserSummary summary = new UserSummary(u1.getId(), "alice@x.com", "Alice", null,
                List.of(), List.of(), null, null);
        when(permissionCacheService.toUserSummary(u1)).thenReturn(summary);

        Page<UserSummary> result = userService.searchUsers("alice", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("alice@x.com");
    }

    @Test
    void searchUsers_truncatesQueryAt50Chars() {
        String longQuery = "a".repeat(60);
        String truncated = "a".repeat(50);
        when(userRepository.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                truncated, truncated)).thenReturn(List.of());

        userService.searchUsers(longQuery, PageRequest.of(0, 20));

        verify(userRepository).findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                truncated, truncated);
    }

    @Test
    void searchUsers_emptyQuery_returnsAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of());

        userService.searchUsers("", PageRequest.of(0, 20));

        verify(userRepository).findAll();
        verify(userRepository, never()).findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCase(any(), any());
    }

    // -----------------------------------------------------------------------
    // getUsersPage with UserFilter (feat/user-filters)
    // -----------------------------------------------------------------------

    /** Builds a user with the given attributes; roles are Role objects keyed by name. */
    private static User user(String email, String displayName, boolean active,
                             LocalDateTime lastLoginAt, String sfUserId, String... roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String rn : roleNames) {
            Role r = new Role();
            r.setName(rn);
            roles.add(r);
        }
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .displayName(displayName)
                .active(active)
                .lastLoginAt(lastLoginAt)
                .sfUserId(sfUserId)
                .roles(roles)
                .build();
    }

    /** Stubs scope passthrough + identity-ish toUserSummary echoing email so we can assert on results. */
    private void stubPassthrough(List<User> users) {
        when(userRepository.findAll()).thenReturn(users);
        when(scopeService.filterByScope(eq(AUTH_USER_ID), anyList()))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(permissionCacheService.toUserSummary(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            List<String> rn = u.getRoles() == null ? List.of()
                    : u.getRoles().stream().map(Role::getName).toList();
            return new UserSummary(u.getId(), u.getEmail(), u.getDisplayName(), null,
                    rn, List.of(), null, null);
        });
    }

    private List<String> emails(Page<UserSummary> page) {
        return page.getContent().stream().map(UserSummary::email).toList();
    }

    @Test
    void getUsersPage_emptyFilter_matchesPreviousBehaviour() {
        User a = user("a@x.com", "Alice", true, null, null);
        User b = user("b@x.com", "Bob", true, null, null);
        stubPassthrough(List.of(a, b));

        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID,
                UserFilter.empty(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(emails(page)).containsExactly("a@x.com", "b@x.com");
    }

    @Test
    void getUsersPage_resultsSortedByEmailAscending() {
        User a = user("zoe@x.com", "Zoe", true, null, null);
        User b = user("amy@x.com", "Amy", true, null, null);
        stubPassthrough(List.of(a, b));

        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID,
                UserFilter.empty(), PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("amy@x.com", "zoe@x.com");
    }

    @Test
    void getUsersPage_qFilter_matchesNameOrEmailCaseInsensitive() {
        User a = user("alice@x.com", "Alice Smith", true, null, null);
        User b = user("bob@x.com", "Bob Jones", true, null, null);
        stubPassthrough(List.of(a, b));

        UserFilter f = new UserFilter("SMITH", List.of(), false, null, null, false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("alice@x.com");
    }

    @Test
    void getUsersPage_qFilter_nullDisplayNameIsSafe() {
        User a = user("alice@x.com", null, true, null, null);
        stubPassthrough(List.of(a));

        UserFilter f = new UserFilter("alice", List.of(), false, null, null, false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("alice@x.com");
    }

    @Test
    void getUsersPage_roleNamesFilter_orWithin() {
        User a = user("a@x.com", "A", true, null, null, "MANAGER");
        User b = user("b@x.com", "B", true, null, null, "EMPLOYEE");
        User c = user("c@x.com", "C", true, null, null, "HR_FULL_CRUD");
        stubPassthrough(List.of(a, b, c));

        UserFilter f = new UserFilter(null, List.of("MANAGER", "HR_FULL_CRUD"),
                false, null, null, false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("a@x.com", "c@x.com");
    }

    @Test
    void getUsersPage_noRolesFilter_keepsOnlyUsersWithoutRoles() {
        User a = user("a@x.com", "A", true, null, null);            // no roles
        User b = user("b@x.com", "B", true, null, null, "EMPLOYEE"); // has role
        stubPassthrough(List.of(a, b));

        UserFilter f = new UserFilter(null, List.of(), true, null, null, false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("a@x.com");
    }

    @Test
    void getUsersPage_permissionFilter_usesAllExpansionViaPermissionCacheService() {
        User a = user("a@x.com", "A", true, null, null, "ASSESSMENT_ADMIN");
        User b = user("b@x.com", "B", true, null, null, "EMPLOYEE");
        stubPassthrough(List.of(a, b));
        // ASSESSMENT_ADMIN holds ASSESS_ALL which expands to include ASSESS_READ.
        when(permissionCacheService.getPermissionsForRoles(List.of("ASSESSMENT_ADMIN")))
                .thenReturn(Set.of("ASSESS_READ", "ASSESS_ALL"));
        when(permissionCacheService.getPermissionsForRoles(List.of("EMPLOYEE")))
                .thenReturn(Set.of("SPARK_NOMINATE"));

        UserFilter f = new UserFilter(null, List.of(), false, "ASSESS_READ", null, false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("a@x.com");
    }

    @Test
    void getUsersPage_statusActive_keepsActiveOnly() {
        User a = user("a@x.com", "A", true, null, null);
        User b = user("b@x.com", "B", false, null, null);
        stubPassthrough(List.of(a, b));

        UserFilter f = new UserFilter(null, List.of(), false, null, "active", false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("a@x.com");
    }

    @Test
    void getUsersPage_statusInactive_keepsInactiveOnly() {
        User a = user("a@x.com", "A", true, null, null);
        User b = user("b@x.com", "B", false, null, null);
        stubPassthrough(List.of(a, b));

        UserFilter f = new UserFilter(null, List.of(), false, null, "INACTIVE", false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("b@x.com");
    }

    @Test
    void getUsersPage_neverLoggedIn_keepsNullLastLogin() {
        User a = user("a@x.com", "A", true, null, null);                              // never
        User b = user("b@x.com", "B", true, LocalDateTime.now().minusDays(1), null);  // logged in
        stubPassthrough(List.of(a, b));

        UserFilter f = new UserFilter(null, List.of(), false, null, null, true, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("a@x.com");
    }

    @Test
    void getUsersPage_staleDays_keepsNeverLoggedInOrOlderThanCutoff() {
        User never = user("never@x.com", "Never", true, null, null);
        User old = user("old@x.com", "Old", true, LocalDateTime.now().minusDays(90), null);
        User recent = user("recent@x.com", "Recent", true, LocalDateTime.now().minusDays(1), null);
        stubPassthrough(List.of(never, old, recent));

        UserFilter f = new UserFilter(null, List.of(), false, null, null, false, 30, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("never@x.com", "old@x.com");
    }

    @Test
    void getUsersPage_syncSourceSaf_keepsSfProvisioned() {
        User saf = user("saf@x.com", "Saf", true, null, "122308");
        User x4 = user("x4@x.com", "X4", true, null, null);
        stubPassthrough(List.of(saf, x4));

        UserFilter f = new UserFilter(null, List.of(), false, null, null, false, null, "SAF");
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("saf@x.com");
    }

    @Test
    void getUsersPage_syncSourceX4auth_keepsNonSf() {
        User saf = user("saf@x.com", "Saf", true, null, "122308");
        User x4 = user("x4@x.com", "X4", true, null, null);
        stubPassthrough(List.of(saf, x4));

        UserFilter f = new UserFilter(null, List.of(), false, null, null, false, null, "X4AUTH");
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("x4@x.com");
    }

    @Test
    void getUsersPage_combinedFilters_allMustHold() {
        User match = user("match@x.com", "Match", true, null, null, "MANAGER");
        User wrongStatus = user("a@x.com", "A", false, null, null, "MANAGER");
        User wrongRole = user("b@x.com", "B", true, null, null, "EMPLOYEE");
        stubPassthrough(List.of(match, wrongStatus, wrongRole));

        UserFilter f = new UserFilter(null, List.of("MANAGER"), false, null, "active", false, null, null);
        Page<UserSummary> page = userService.getUsersPage(null, AUTH_USER_ID, f, PageRequest.of(0, 20));

        assertThat(emails(page)).containsExactly("match@x.com");
    }
}
