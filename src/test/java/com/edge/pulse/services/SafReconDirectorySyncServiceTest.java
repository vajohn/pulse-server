package com.edge.pulse.services;

import com.edge.pulse.configs.SafReconProperties;
import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.safrecon.SafReconEmployee;
import com.edge.pulse.data.dto.safrecon.SafReconEmployeePage;
import com.edge.pulse.data.dto.safrecon.SafReconOrgUnit;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.SfSyncState;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SafReconDirectorySyncServiceTest {

    SafReconClient client;
    UserRepository userRepo;
    RoleRepository roleRepo;
    UserSfProfileRepository sfProfileRepo;
    OrganizationalUnitRepository orgUnitRepo;
    TitleRepository titleRepo;
    SfSyncStateRepository syncStateRepo;
    FormCacheService cacheService;
    SafReconProperties props;
    SafReconDirectorySyncService svc;

    private static Role role(String name) {
        Role r = new Role();
        r.setId(UUID.randomUUID());
        r.setName(name);
        return r;
    }

    @BeforeEach
    void setUp() {
        client = mock(SafReconClient.class);
        userRepo = mock(UserRepository.class);
        roleRepo = mock(RoleRepository.class);
        sfProfileRepo = mock(UserSfProfileRepository.class);
        orgUnitRepo = mock(OrganizationalUnitRepository.class);
        titleRepo = mock(TitleRepository.class);
        syncStateRepo = mock(SfSyncStateRepository.class);
        cacheService = mock(FormCacheService.class);
        props = new SafReconProperties();
        props.setDeactivateFloor(1);

        when(client.isConfigured()).thenReturn(true);
        when(syncStateRepo.save(any(SfSyncState.class))).thenAnswer(i -> i.getArgument(0));
        when(orgUnitRepo.findByOrgUnitCode(anyString())).thenReturn(Optional.empty());
        when(orgUnitRepo.save(any(OrganizationalUnit.class))).thenAnswer(i -> i.getArgument(0));
        when(titleRepo.findByName(anyString())).thenReturn(Optional.empty());
        when(titleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(roleRepo.findByName("EMPLOYEE")).thenReturn(Optional.of(role("EMPLOYEE")));
        when(roleRepo.findByName("MANAGER")).thenReturn(Optional.of(role("MANAGER")));
        when(userRepo.findWithRolesByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(sfProfileRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findAll()).thenReturn(List.of());

        svc = new SafReconDirectorySyncService(client, userRepo, roleRepo, sfProfileRepo, orgUnitRepo,
                titleRepo, syncStateRepo, cacheService, props);
    }

    @Test
    void fullSync_buildsOrgTreeAndLinksManager() {
        UUID rootId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        when(client.fetchOrgUnits()).thenReturn(List.of(
                new SafReconOrgUnit(rootId, "EDGE_GROUP", "EDGE Group", "GROUP", null, "", 0, null),
                new SafReconOrgUnit(entityId, "13000", "EDGE Missiles", "ENTITY", rootId, "/EDGE_GROUP", 1, "13000")
        ));

        UUID mgrId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        SafReconEmployee mgr = new SafReconEmployee(mgrId, "100", "Mona", "Ali", "Mona Ali",
                "mona.ali@edge.ae", "ACTIVE", "FULL_TIME", null, null, entityId,
                "Avionics", "13000", "Director", "Engineering");
        SafReconEmployee emp = new SafReconEmployee(empId, "101", "Sara", "Noor", "Sara Noor",
                "sara.noor@edge.ae", "ACTIVE", "FULL_TIME", null, mgrId, entityId,
                "Avionics", "13000", "Engineer", "Engineering");
        when(client.fetchEmployeesPage(eq(0), anyInt()))
                .thenReturn(new SafReconEmployeePage(List.of(mgr, emp), 2));

        DirectorySyncResultDto result = svc.fullSync();

        assertThat(result.usersProcessed()).isEqualTo(2);
        assertThat(result.orgUnitsProcessed()).isEqualTo(2);
        // manager linked: the employee's saved User has manager set to Mona's User
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepo, atLeast(2)).save(cap.capture());
        boolean linked = cap.getAllValues().stream()
                .anyMatch(u -> "sara.noor@edge.ae".equals(u.getEmail()) && u.getManager() != null
                        && "mona.ali@edge.ae".equals(u.getManager().getEmail()));
        assertThat(linked).isTrue();
    }

    @Test
    void fullSync_skipsDeactivationBelowFloor() {
        props.setDeactivateFloor(10);
        when(client.fetchOrgUnits()).thenReturn(List.of());
        SafReconEmployee one = new SafReconEmployee(UUID.randomUUID(), "1", "A", "B", "A B",
                "a.b@edge.ae", "ACTIVE", null, null, null, null, null, null, null, null);
        when(client.fetchEmployeesPage(eq(0), anyInt()))
                .thenReturn(new SafReconEmployeePage(List.of(one), 1));

        User stale = new User();
        stale.setId(UUID.randomUUID());
        stale.setEmail("old.user@edge.ae");
        stale.setActive(true);
        when(userRepo.findAll()).thenReturn(List.of(stale));

        DirectorySyncResultDto result = svc.fullSync();

        // only 1 employee returned, floor is 10 → no deactivation
        assertThat(result.usersDeactivated()).isZero();
        assertThat(stale.isActive()).isTrue();
    }

    @Test
    void fullSync_skipsDuplicateEmails_noOverwriteNoDeactivate() {
        props.setDeactivateFloor(0); // deactivation enabled
        when(client.fetchOrgUnits()).thenReturn(List.of());
        SafReconEmployee e1 = new SafReconEmployee(UUID.randomUUID(), "1", "Real", "One", "Real One",
                "dup@edge.ae", "ACTIVE", null, null, null, null, null, null, null, null);
        SafReconEmployee e2 = new SafReconEmployee(UUID.randomUUID(), "2", "Real", "Two", "Real Two",
                "DUP@edge.ae", "ACTIVE", null, null, null, null, null, null, null, null);
        when(client.fetchEmployeesPage(eq(0), anyInt()))
                .thenReturn(new SafReconEmployeePage(List.of(e1, e2), 2));

        // an existing Pulse user already owns that email (logged in earlier)
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("dup@edge.ae");
        existing.setDisplayName("Original");
        existing.setActive(true);
        when(userRepo.findAll()).thenReturn(List.of(existing));

        DirectorySyncResultDto result = svc.fullSync();

        // neither duplicate was created/enriched; existing user untouched + not deactivated
        assertThat(result.usersCreated()).isZero();
        assertThat(result.usersUpdated()).isZero();
        verify(userRepo, never()).save(argThat(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase("dup@edge.ae")));
        assertThat(existing.getDisplayName()).isEqualTo("Original");
        assertThat(existing.isActive()).isTrue();
        assertThat(result.errors()).isEqualTo(2); // 2 duplicate records skipped, rolled into errors
    }

    @Test
    void fullSync_promotesExistingEmployeeToManager() {
        when(client.fetchOrgUnits()).thenReturn(List.of());
        UUID bossId = UUID.randomUUID();
        SafReconEmployee boss = new SafReconEmployee(bossId, "10", "Boss", "Person", "Boss Person",
                "boss@edge.ae", "ACTIVE", null, null, null, null, null, null, "Engineer", null);
        SafReconEmployee report = new SafReconEmployee(UUID.randomUUID(), "11", "Rep", "Ort", "Rep Ort",
                "rep@edge.ae", "ACTIVE", null, null, bossId, null, null, null, "Engineer", null);
        when(client.fetchEmployeesPage(eq(0), anyInt()))
                .thenReturn(new SafReconEmployeePage(List.of(boss, report), 2));

        // boss logged in earlier as a plain EMPLOYEE; non-managerial title — promoted via managerId signal
        User bossUser = new User();
        bossUser.setId(UUID.randomUUID());
        bossUser.setEmail("boss@edge.ae");
        bossUser.setActive(true);
        bossUser.setRoles(new HashSet<>(Set.of(role("EMPLOYEE"))));
        when(userRepo.findWithRolesByEmail("boss@edge.ae")).thenReturn(Optional.of(bossUser));

        svc.fullSync();

        assertThat(bossUser.getRoles()).extracting(Role::getName).contains("EMPLOYEE", "MANAGER");
    }

    @Test
    void fullSync_demotesManagerAndProtectsManualGrants() {
        when(client.fetchOrgUnits()).thenReturn(List.of());
        SafReconEmployee solo = new SafReconEmployee(UUID.randomUUID(), "20", "Solo", "Dev", "Solo Dev",
                "solo@edge.ae", "ACTIVE", null, null, null, null, null, null, "Engineer", null);
        when(client.fetchEmployeesPage(eq(0), anyInt()))
                .thenReturn(new SafReconEmployeePage(List.of(solo), 1));

        // existing user had MANAGER (now manages nobody, non-managerial title) + a manual HR grant
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail("solo@edge.ae");
        u.setActive(true);
        u.setRoles(new HashSet<>(Set.of(role("EMPLOYEE"), role("MANAGER"), role("HR_FULL_CRUD"))));
        when(userRepo.findWithRolesByEmail("solo@edge.ae")).thenReturn(Optional.of(u));

        svc.fullSync();

        assertThat(u.getRoles()).extracting(Role::getName)
                .contains("EMPLOYEE", "HR_FULL_CRUD")
                .doesNotContain("MANAGER");
    }
}
