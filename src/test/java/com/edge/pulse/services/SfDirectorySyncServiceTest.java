package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.SfUserRecord;
import com.edge.pulse.data.enums.OrgLevel;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.SfSyncState;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.UserSfProfile;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.SfSyncStateRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.UserSfProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SfDirectorySyncServiceTest {

    @Mock SfODataClient sfClient;
    @Mock UserRepository userRepo;
    @Mock UserSfProfileRepository sfProfileRepo;
    @Mock OrganizationalUnitRepository orgUnitRepo;
    @Mock SfSyncStateRepository syncStateRepo;
    @Mock FormCacheService cacheService;

    SfDirectorySyncService service;

    @BeforeEach
    void setUp() {
        service = new SfDirectorySyncService(sfClient, userRepo, sfProfileRepo, orgUnitRepo, syncStateRepo, cacheService);
        ReflectionTestUtils.setField(service, "dryRun", false);

        // Default: no existing org units (triggers creates) — lenient so tests that skip the tree don't fail
        lenient().when(orgUnitRepo.findByOrgUnitCode(anyString())).thenReturn(Optional.empty());
        lenient().when(orgUnitRepo.save(any(OrganizationalUnit.class))).thenAnswer(inv -> {
            OrganizationalUnit ou = inv.getArgument(0);
            if (ou.getId() == null) ou.setId(UUID.randomUUID());
            return ou;
        });
        lenient().when(orgUnitRepo.count()).thenReturn(0L, 5L); // before and after

        // SF profile repo — lenient: only used by upsertUsers tests
        lenient().when(sfProfileRepo.findAllById(any())).thenReturn(List.of());
        lenient().when(sfProfileRepo.save(any(UserSfProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        // userRepo batch lookup used by scoped profile pre-load
        lenient().when(userRepo.findAllBySfUserIdIn(any())).thenReturn(List.of());

        // Sync state — lenient because only fullSync/deltaSync tests use it
        lenient().when(syncStateRepo.save(any(SfSyncState.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── SfUserRecord helpers ──────────────────────────────────────────────

    @Test
    void extractCode_parsesParenthesisPattern() {
        assertThat(SfUserRecord.extractCode("EDGE Missiles and Weapons (13000)")).isEqualTo("13000");
        assertThat(SfUserRecord.extractCode("Testing and Software (10001368)")).isEqualTo("10001368");
        assertThat(SfUserRecord.extractCode("NoCode")).isEqualTo("NoCode");
        assertThat(SfUserRecord.extractCode(null)).isNull();
    }

    @Test
    void extractName_stripsCode() {
        assertThat(SfUserRecord.extractName("EDGE Missiles and Weapons (13000)")).isEqualTo("EDGE Missiles and Weapons");
        assertThat(SfUserRecord.extractName("NoCode")).isEqualTo("NoCode");
    }

    @Test
    void effectiveEmail_stripsXPrefix() {
        SfUserRecord u = user("1", "X-john.doe@edge.com", null);
        assertThat(u.effectiveEmail()).isEqualTo("john.doe@edge.com");
    }

    @Test
    void effectiveEmail_usesEmailFallback() {
        SfUserRecord u = user("2", "not-an-email", "john@edge.com");
        assertThat(u.effectiveEmail()).isEqualTo("john@edge.com");
    }

    @Test
    void isActive_returnsFalse_forAlumni() {
        SfUserRecord u = userWithStatus("3", true, "active");
        assertThat(u.isActive()).isFalse();
    }

    @Test
    void isActive_returnsFalse_forInactiveStatus() {
        SfUserRecord u = userWithStatus("4", false, "inactive");
        assertThat(u.isActive()).isFalse();
    }

    // ── Org tree building ─────────────────────────────────────────────────

    @Test
    void buildOrgTree_createsHierarchyFromUserFields() {
        List<SfUserRecord> users = List.of(
                fullUser("1", "u1@edge.com", "EDGE Missiles (13000)", "Al Tariq (4600)",
                         "Testing (10001368)", "QA (10010992)")
        );

        SfDirectorySyncService.OrgTreeResult result = service.buildAndUpsertOrgTree(users);

        // Verify org unit creations: root + unassigned + group + entity + orgunit + team = 6 saves
        verify(orgUnitRepo, atLeast(4)).save(any(OrganizationalUnit.class));

        // Tree cache should have entries for G/E/OU/T
        assertThat(result.cache()).containsKey("G:13000");
        assertThat(result.cache()).containsKey("E:4600");
        assertThat(result.cache()).containsKey("OU:10001368");
        assertThat(result.cache()).containsKey("T:10010992");
        assertThat(result.unassigned()).isNotNull();
    }

    @Test
    void buildOrgTree_deduplicatesOrgUnitsAcrossUsers() {
        List<SfUserRecord> users = List.of(
                fullUser("1", "u1@edge.com", "EDGE Missiles (13000)", "Al Tariq (4600)", "Testing (10001368)", "QA (10010992)"),
                fullUser("2", "u2@edge.com", "EDGE Missiles (13000)", "Al Tariq (4600)", "Testing (10001368)", "Dev (10010993)")
        );

        SfDirectorySyncService.OrgTreeResult result = service.buildAndUpsertOrgTree(users);

        // G, E, OU should be deduped; only two different TEAM nodes
        assertThat(result.cache()).containsKeys("G:13000", "E:4600", "OU:10001368", "T:10010992", "T:10010993");
        assertThat(result.cache()).hasSize(5);
    }

    @Test
    void buildOrgTree_skipsUsersWithNoGroup() {
        // User with no custom02 → can't place in tree → no group cache entry
        List<SfUserRecord> users = List.of(user("1", "u@edge.com", null));

        SfDirectorySyncService.OrgTreeResult result = service.buildAndUpsertOrgTree(users);

        assertThat(result.cache()).isEmpty();
        // Root + Unassigned still created
        verify(orgUnitRepo, atLeast(2)).save(any());
    }

    // ── User upsert ───────────────────────────────────────────────────────

    @Test
    void upsertUsers_createsNewUser() {
        SfUserRecord sfUser = fullUser("101", "X-emp@edge.com", "EDGE (13000)", "Al Tariq (4600)", "Test (10001368)", "QA (10010)");
        when(userRepo.findBySfUserId("101")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("emp@edge.com")).thenReturn(Optional.empty());
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SfDirectorySyncService.OrgTreeResult tree = stubTree();

        SfDirectorySyncService.UserSyncResult result = service.upsertUsers(List.of(sfUser), tree);

        verify(userRepo).save(any(User.class));
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
    }

    @Test
    void upsertUsers_updatesExistingUserByEmail() {
        SfUserRecord sfUser = fullUser("102", "X-existing@edge.com", "EDGE (13000)", "Al Tariq (4600)", "Test (10001368)", "QA (10010)");

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setEmail("existing@edge.com");
        existing.setSfUserId("102");

        when(userRepo.findBySfUserId("102")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SfDirectorySyncService.UserSyncResult result = service.upsertUsers(
                List.of(sfUser), stubTree());

        verify(userRepo).save(existing);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isZero();
    }

    @Test
    void upsertUsers_placesUserInUnassignedWhenNoOrgFields() {
        // User with no org fields at all
        SfUserRecord sfUser = new SfUserRecord("999", "X-noorg@edge.com", "No", "Org", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, null, null);

        when(userRepo.findBySfUserId("999")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("noorg@edge.com")).thenReturn(Optional.empty());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepo.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        SfDirectorySyncService.OrgTreeResult tree = stubTree();
        service.upsertUsers(List.of(sfUser), tree);

        assertThat(captor.getValue().getOrgUnit()).isSameAs(tree.unassigned());
    }

    @Test
    void upsertUsers_deactivatesAlumni() {
        SfUserRecord sfUser = new SfUserRecord("alumni1", "X-left@edge.com", "Ex", "Emp", null,
                null, true, "2024-01-01", "inactive", null, null, null, null, null, null, null, null, null, null);

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setSfUserId("alumni1");

        when(userRepo.findBySfUserId("alumni1")).thenReturn(Optional.of(existing));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SfDirectorySyncService.UserSyncResult result = service.upsertUsers(List.of(sfUser), stubTree());

        assertThat(existing.isActive()).isFalse();
        assertThat(result.deactivated()).isEqualTo(1);
    }

    @Test
    void upsertUsers_skipsUserWithNoEmail() {
        SfUserRecord sfUser = new SfUserRecord("noemail", null, "No", "Email", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, null, null);

        SfDirectorySyncService.UserSyncResult result = service.upsertUsers(List.of(sfUser), stubTree());

        verify(userRepo, never()).save(any());
        assertThat(result.errors()).isEqualTo(1);
    }

    @Test
    void upsertUsers_toleratesIndividualUserErrors() {
        // Good user
        SfUserRecord good = fullUser("g1", "X-good@edge.com", "EDGE (13000)", "Al Tariq (4600)", "Test (10001368)", "QA (10010)");
        // Bad user — will trigger an exception in findBySfUserId
        SfUserRecord bad = new SfUserRecord("bad", "X-bad@edge.com", "Bad", "User", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, null, null);

        when(userRepo.findBySfUserId("bad")).thenThrow(new RuntimeException("DB error"));
        when(userRepo.findBySfUserId("g1")).thenReturn(Optional.empty());
        when(userRepo.findByEmail("good@edge.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SfDirectorySyncService.UserSyncResult result = service.upsertUsers(List.of(bad, good), stubTree());

        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
    }

    // ── Manager linking ───────────────────────────────────────────────────

    @Test
    void linkManagers_setsManagerReference() {
        SfUserRecord.SfManagerRef mgr = new SfUserRecord.SfManagerRef("mgr1");
        SfUserRecord emp = new SfUserRecord("emp1", "X-emp@edge.com", "Emp", "One", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, null, mgr);

        User empUser = new User(); empUser.setId(UUID.randomUUID());
        User mgrUser = new User(); mgrUser.setId(UUID.randomUUID());

        when(userRepo.findBySfUserId("emp1")).thenReturn(Optional.of(empUser));
        when(userRepo.findBySfUserId("mgr1")).thenReturn(Optional.of(mgrUser));
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.linkManagers(List.of(emp));

        assertThat(empUser.getManager()).isSameAs(mgrUser);
    }

    @Test
    void linkManagers_skipsWhenManagerNotYetSynced() {
        SfUserRecord.SfManagerRef mgr = new SfUserRecord.SfManagerRef("mgr-missing");
        SfUserRecord emp = new SfUserRecord("emp2", "X-emp2@edge.com", "Emp", "Two", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, null, mgr);

        User empUser = new User(); empUser.setId(UUID.randomUUID());
        when(userRepo.findBySfUserId("emp2")).thenReturn(Optional.of(empUser));
        when(userRepo.findBySfUserId("mgr-missing")).thenReturn(Optional.empty());

        service.linkManagers(List.of(emp));

        verify(userRepo, never()).save(any());
        assertThat(empUser.getManager()).isNull();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private SfDirectorySyncService.OrgTreeResult stubTree() {
        OrganizationalUnit unassigned = OrganizationalUnit.builder()
                .id(UUID.randomUUID())
                .orgUnitCode("SF_UNASSIGNED")
                .orgUnitName("Unassigned")
                .orgLevel(OrgLevel.GROUP)
                .path("/EDGE_GROUP_ROOT")
                .depth(1)
                .build();
        return new SfDirectorySyncService.OrgTreeResult(2, 2, 0, unassigned, new java.util.LinkedHashMap<>());
    }

    private SfUserRecord user(String userId, String username, String email) {
        return new SfUserRecord(userId, username, "First", "Last", null, null,
                false, null, "active", null, null, null, null, null, null, null, null, email, null);
    }

    private SfUserRecord userWithStatus(String userId, boolean isAlumni, String status) {
        return new SfUserRecord(userId, "X-u@edge.com", "F", "L", null, null,
                isAlumni, null, status, null, null, null, null, null, null, null, null, null, null);
    }

    private SfUserRecord fullUser(String userId, String username,
                                  String custom02, String custom01,
                                  String division, String department) {
        return new SfUserRecord(userId, username, "First", "Last", "Engineer", null,
                false, null, "active", department, division, custom01, custom02,
                null, "Civilian", "Engineering", null, null, null);
    }
}
