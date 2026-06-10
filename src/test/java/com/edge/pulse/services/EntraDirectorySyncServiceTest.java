package com.edge.pulse.services;

import com.edge.pulse.data.dto.DirectorySyncResultDto;
import com.edge.pulse.data.dto.GraphGroup;
import com.edge.pulse.data.dto.GraphUserProfile;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntraDirectorySyncServiceTest {

    @Mock MicrosoftGraphService graphService;
    @Mock UserProvisioningService userProvisioningService;
    @Mock OrganizationalUnitRepository orgUnitRepo;
    @Mock UserRepository userRepo;

    EntraDirectorySyncService service;

    private static final String APP_TOKEN = "app-token";

    @BeforeEach
    void setUp() {
        service = new EntraDirectorySyncService(graphService, userProvisioningService, orgUnitRepo, userRepo);
        ReflectionTestUtils.setField(service, "staleHours", 23);
    }

    // -------------------------------------------------------------------------
    // syncUsers tests
    // -------------------------------------------------------------------------

    @Test
    void syncUsers_skipsRecentlySyncedUsers() {
        GraphUserProfile gUser = profile("u1", "test@edge.com", true);
        when(graphService.fetchAllUsers(APP_TOKEN)).thenReturn(List.of(gUser));

        User u = new User();
        u.setAzureAdId("u1");
        u.setEntraLastSyncedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1)); // recently synced
        when(userRepo.findByAzureAdId("u1")).thenReturn(Optional.of(u));

        DirectorySyncResultDto result = service.syncUsers(APP_TOKEN);

        verify(userProvisioningService, never()).provisionOrUpdateUser(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString(), anyString(), anyString());
        assertThat(result.usersProcessed()).isEqualTo(1);
        assertThat(result.usersCreated()).isZero();
        assertThat(result.usersUpdated()).isZero();
    }

    @Test
    void syncUsers_provisionsStaleUser() {
        GraphUserProfile gUser = profile("u2", "stale@edge.com", true);
        when(graphService.fetchAllUsers(APP_TOKEN)).thenReturn(List.of(gUser));

        User u = new User();
        u.setAzureAdId("u2");
        u.setEntraLastSyncedAt(null); // never synced
        when(userRepo.findByAzureAdId("u2")).thenReturn(Optional.of(u));
        when(graphService.fetchUserManagerById(APP_TOKEN, "u2")).thenReturn(Optional.empty());
        when(userProvisioningService.provisionOrUpdateUser(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), isNull()))
                .thenReturn(u);

        DirectorySyncResultDto result = service.syncUsers(APP_TOKEN);

        verify(userProvisioningService).provisionOrUpdateUser(
                eq("u2"), eq("stale@edge.com"), eq("Stale User"), any(),
                any(), any(), any(), any(), any(), any(), isNull());
        assertThat(result.usersUpdated()).isEqualTo(1);
    }

    @Test
    void syncUsers_deactivatesDisabledAzureAdUser() {
        GraphUserProfile gUser = profile("u3", "disabled@edge.com", false); // accountEnabled=false
        when(graphService.fetchAllUsers(APP_TOKEN)).thenReturn(List.of(gUser));
        when(userRepo.findByAzureAdId("u3")).thenReturn(Optional.empty()); // new user
        when(graphService.fetchUserManagerById(APP_TOKEN, "u3")).thenReturn(Optional.empty());

        User saved = new User();
        saved.setAzureAdId("u3");
        saved.setActive(true);
        when(userProvisioningService.provisionOrUpdateUser(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);
        when(userRepo.findByAzureAdId("u3")).thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved)); // second call in deactivation path

        DirectorySyncResultDto result = service.syncUsers(APP_TOKEN);

        assertThat(result.usersDeactivated()).isEqualTo(1);
        assertThat(saved.isActive()).isFalse();
        verify(userRepo, atLeastOnce()).save(saved);
    }

    @Test
    void syncUsers_linksManagerFromGraph() {
        GraphUserProfile gUser    = profile("u4", "emp@edge.com", true);
        GraphUserProfile gManager = profile("m1", "mgr@edge.com", true);

        when(graphService.fetchAllUsers(APP_TOKEN)).thenReturn(List.of(gUser));
        when(userRepo.findByAzureAdId("u4")).thenReturn(Optional.empty()); // new
        when(graphService.fetchUserManagerById(APP_TOKEN, "u4")).thenReturn(Optional.of(gManager));

        User provisioned = new User();
        when(userProvisioningService.provisionOrUpdateUser(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), anyString()))
                .thenReturn(provisioned);

        service.syncUsers(APP_TOKEN);

        verify(userProvisioningService).provisionOrUpdateUser(
                eq("u4"), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), eq("m1"));
    }

    @Test
    void syncUsers_swallowsIndividualUserErrors() {
        GraphUserProfile badUser  = profile("bad", "bad@edge.com", true);
        GraphUserProfile goodUser = profile("good", "good@edge.com", true);

        when(graphService.fetchAllUsers(APP_TOKEN)).thenReturn(List.of(badUser, goodUser));
        when(userRepo.findByAzureAdId("bad")).thenReturn(Optional.empty());
        when(userRepo.findByAzureAdId("good")).thenReturn(Optional.empty());

        when(graphService.fetchUserManagerById(APP_TOKEN, "bad"))
                .thenThrow(new RuntimeException("Graph timeout"));
        when(graphService.fetchUserManagerById(APP_TOKEN, "good"))
                .thenReturn(Optional.empty());
        when(userProvisioningService.provisionOrUpdateUser(
                eq("good"), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new User());

        DirectorySyncResultDto result = service.syncUsers(APP_TOKEN);

        assertThat(result.errors()).isEqualTo(1);
        assertThat(result.usersCreated()).isEqualTo(1); // goodUser
    }

    // -------------------------------------------------------------------------
    // syncOrgUnitsFromGroups tests
    // -------------------------------------------------------------------------

    @Test
    void syncOrgUnitsFromGroups_createsNewOrgUnit() {
        GraphGroup group = new GraphGroup("g1", "Alpha Squadron", "desc");
        when(graphService.fetchAllGroups(APP_TOKEN)).thenReturn(List.of(group));
        when(orgUnitRepo.findByEntraGroupId("g1")).thenReturn(Optional.empty());
        when(orgUnitRepo.findByOrgUnitCode("Alpha Squadron")).thenReturn(Optional.empty());

        DirectorySyncResultDto result = service.syncOrgUnitsFromGroups(APP_TOKEN);

        ArgumentCaptor<OrganizationalUnit> captor = ArgumentCaptor.forClass(OrganizationalUnit.class);
        verify(orgUnitRepo).save(captor.capture());
        OrganizationalUnit created = captor.getValue();
        assertThat(created.getEntraGroupId()).isEqualTo("g1");
        assertThat(created.getOrgUnitName()).isEqualTo("Alpha Squadron");
        assertThat(created.isActive()).isTrue();
        assertThat(result.orgUnitsCreated()).isEqualTo(1);
        assertThat(result.orgUnitsUpdated()).isZero();
    }

    @Test
    void syncOrgUnitsFromGroups_updatesExistingOrgUnit() {
        GraphGroup group = new GraphGroup("g2", "Beta Wing (renamed)", "desc");

        OrganizationalUnit existing = new OrganizationalUnit();
        existing.setEntraGroupId("g2");
        existing.setOrgUnitName("Beta Wing");

        when(graphService.fetchAllGroups(APP_TOKEN)).thenReturn(List.of(group));
        when(orgUnitRepo.findByEntraGroupId("g2")).thenReturn(Optional.of(existing));

        DirectorySyncResultDto result = service.syncOrgUnitsFromGroups(APP_TOKEN);

        assertThat(existing.getOrgUnitName()).isEqualTo("Beta Wing (renamed)");
        verify(orgUnitRepo).save(existing);
        assertThat(result.orgUnitsUpdated()).isEqualTo(1);
        assertThat(result.orgUnitsCreated()).isZero();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private GraphUserProfile profile(String id, String mail, boolean accountEnabled) {
        String name = mail.split("@")[0].replace(".", " ");
        name = Character.toUpperCase(name.charAt(0)) + name.substring(1) + " User";
        return new GraphUserProfile(id, name, mail, mail, "Engineer", "Dept", "EMP-" + id,
                "Dubai", "EDGE", accountEnabled);
    }
}
