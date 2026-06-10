package com.edge.pulse.services;

import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgUnitScopeServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationalUnitRepository orgUnitRepository;

    private OrgUnitScopeService service;

    private static final UUID USER_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ORG_ID_A   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ORG_ID_B   = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID OWN_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    @BeforeEach
    void setUp() {
        service = new OrgUnitScopeService(userRepository, orgUnitRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Branch 1: SCOPE_ORG_WIDE ─────────────────────────────────────────────

    @Test
    void resolveAccessibleOrgUnitIds_scopeOrgWide_returnsAllActiveIds() {
        when(orgUnitRepository.findAllActiveIds()).thenReturn(List.of(ORG_ID_A, ORG_ID_B));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_ORG_WIDE", "USR_READ"));

        assertThat(result).containsExactlyInAnyOrder(ORG_ID_A, ORG_ID_B);
        verify(orgUnitRepository).findAllActiveIds();
        verify(orgUnitRepository, never()).findAllActiveIdsByCompanyCode(any());
        verify(orgUnitRepository, never()).findAllActiveIdsByPathPrefix(any(), any());
        verify(orgUnitRepository, never()).findOrgUnitIdByUserId(any());
    }

    // ── Branch 2: SCOPE_ENTITY ───────────────────────────────────────────────

    @Test
    void resolveAccessibleOrgUnitIds_scopeEntity_returnsIdsByCompanyCode() {
        when(userRepository.findCompanyCodeById(USER_ID)).thenReturn(Optional.of("UAE-HQ"));
        when(orgUnitRepository.findAllActiveIdsByCompanyCode("UAE-HQ"))
                .thenReturn(List.of(ORG_ID_A, ORG_ID_B));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_ENTITY", "USR_READ"));

        assertThat(result).containsExactlyInAnyOrder(ORG_ID_A, ORG_ID_B);
        verify(orgUnitRepository).findAllActiveIdsByCompanyCode("UAE-HQ");
    }

    @Test
    void resolveAccessibleOrgUnitIds_scopeEntityNullCompanyCode_fallsThroughToScopeTeam() {
        // No SCOPE_TEAM in authorities, so falls through to own-org-unit
        when(userRepository.findCompanyCodeById(USER_ID)).thenReturn(Optional.empty());
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_ENTITY"));

        // Falls through past SCOPE_TEAM (not in authorities) to own-org-unit
        assertThat(result).containsExactly(OWN_ORG_ID);
        verify(orgUnitRepository, never()).findAllActiveIdsByCompanyCode(any());
    }

    @Test
    void resolveAccessibleOrgUnitIds_scopeEntityNullCompanyCode_withScopeTeam_usesTeamPath() {
        when(userRepository.findCompanyCodeById(USER_ID)).thenReturn(Optional.empty());
        when(orgUnitRepository.findPathPrefixByUserId(USER_ID)).thenReturn(Optional.of("root/team1"));
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));
        when(orgUnitRepository.findAllActiveIdsByPathPrefix("root/team1", OWN_ORG_ID))
                .thenReturn(List.of(OWN_ORG_ID, ORG_ID_A));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_ENTITY", "SCOPE_TEAM"));

        assertThat(result).containsExactlyInAnyOrder(OWN_ORG_ID, ORG_ID_A);
        verify(orgUnitRepository).findAllActiveIdsByPathPrefix("root/team1", OWN_ORG_ID);
    }

    // ── Branch 3: SCOPE_TEAM ─────────────────────────────────────────────────

    @Test
    void resolveAccessibleOrgUnitIds_scopeTeam_returnsSubtreeIds() {
        when(orgUnitRepository.findPathPrefixByUserId(USER_ID)).thenReturn(Optional.of("root/team1"));
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));
        when(orgUnitRepository.findAllActiveIdsByPathPrefix("root/team1", OWN_ORG_ID))
                .thenReturn(List.of(OWN_ORG_ID, ORG_ID_A));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_TEAM", "USR_READ"));

        assertThat(result).containsExactlyInAnyOrder(OWN_ORG_ID, ORG_ID_A);
        verify(orgUnitRepository).findAllActiveIdsByPathPrefix("root/team1", OWN_ORG_ID);
    }

    @Test
    void resolveAccessibleOrgUnitIds_scopeTeam_noPathPrefix_fallsToOwnOrgUnit() {
        when(orgUnitRepository.findPathPrefixByUserId(USER_ID)).thenReturn(Optional.empty());
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("SCOPE_TEAM"));

        // path is empty but ownId is also absent from the pair check, falls to no-scope branch
        assertThat(result).containsExactly(OWN_ORG_ID);
        verify(orgUnitRepository, never()).findAllActiveIdsByPathPrefix(any(), any());
    }

    // ── Branch 4: No scope ───────────────────────────────────────────────────

    @Test
    void resolveAccessibleOrgUnitIds_noScope_returnsOwnOrgUnitOnly() {
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));

        List<UUID> result = service.resolveAccessibleOrgUnitIds(
                USER_ID, Set.of("USR_READ", "FORM_READ"));

        assertThat(result).containsExactly(OWN_ORG_ID);
        verify(orgUnitRepository).findOrgUnitIdByUserId(USER_ID);
        verify(orgUnitRepository, never()).findAllActiveIds();
        verify(orgUnitRepository, never()).findAllActiveIdsByCompanyCode(any());
        verify(orgUnitRepository, never()).findAllActiveIdsByPathPrefix(any(), any());
    }

    @Test
    void resolveAccessibleOrgUnitIds_noScope_userHasNoOrgUnit_returnsEmpty() {
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.empty());

        List<UUID> result = service.resolveAccessibleOrgUnitIds(USER_ID, Set.of());

        assertThat(result).isEmpty();
    }

    // ── hasBroadScope ────────────────────────────────────────────────────────

    @Test
    void hasBroadScope_scopeOrgWide_returnsTrue() {
        assertThat(service.hasBroadScope(Set.of("SCOPE_ORG_WIDE", "USR_READ"))).isTrue();
    }

    @Test
    void hasBroadScope_scopeEntity_returnsTrue() {
        assertThat(service.hasBroadScope(Set.of("SCOPE_ENTITY"))).isTrue();
    }

    @Test
    void hasBroadScope_scopeTeamOnly_returnsFalse() {
        assertThat(service.hasBroadScope(Set.of("SCOPE_TEAM", "USR_READ"))).isFalse();
    }

    @Test
    void hasBroadScope_noScope_returnsFalse() {
        assertThat(service.hasBroadScope(Set.of("USR_READ", "FORM_READ"))).isFalse();
    }

    // ── resolveAccessibleOrgUnitIdsFromContext ────────────────────────────────

    @Test
    void resolveFromContext_scopeOrgWide_usesSecurityContextAuthorities() {
        setSecurityContext("SCOPE_ORG_WIDE", "USR_READ");
        when(orgUnitRepository.findAllActiveIds()).thenReturn(List.of(ORG_ID_A, ORG_ID_B));

        Set<UUID> result = service.resolveAccessibleOrgUnitIdsFromContext(USER_ID);

        assertThat(result).containsExactlyInAnyOrder(ORG_ID_A, ORG_ID_B);
    }

    @Test
    void resolveFromContext_noSecurityContext_returnsOwnOrgUnit() {
        SecurityContextHolder.clearContext();
        when(orgUnitRepository.findOrgUnitIdByUserId(USER_ID)).thenReturn(Optional.of(OWN_ORG_ID));

        Set<UUID> result = service.resolveAccessibleOrgUnitIdsFromContext(USER_ID);

        assertThat(result).containsExactly(OWN_ORG_ID);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setSecurityContext(String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        var auth = new UsernamePasswordAuthenticationToken(USER_ID, null, grantedAuthorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
