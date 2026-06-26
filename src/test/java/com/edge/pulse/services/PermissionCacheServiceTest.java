package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.repositories.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PermissionCacheServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SetOperations<String, String> setOps;
    @Mock private Cursor<String> scanCursor;

    private PermissionCacheService service;

    private static final String ROLE = "HR_FULL_CRUD";
    private static final String CACHE_KEY = "role:permissions:" + ROLE;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        CacheTtlProperties props = new CacheTtlProperties(); // uses default permissionTtlMinutes = 5
        service = new PermissionCacheService(redisTemplate, permissionRepository, props);
    }

    // ── Cache hit ────────────────────────────────────────────────────────────

    @Test
    void getPermissionsForRoles_cacheHit_returnsFromRedisWithoutDbCall() {
        when(setOps.members(CACHE_KEY)).thenReturn(Set.of("USR_READ", "FORM_READ"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsExactlyInAnyOrder("USR_READ", "FORM_READ");
        verify(permissionRepository, never()).findPermissionNamesByRoleName(any());
        verify(setOps, never()).add(any(), any(String[].class));
    }

    // ── Cache miss → DB load → populate cache ────────────────────────────────

    @Test
    void getPermissionsForRoles_cacheMiss_loadsFromDbAndPopulatesCache() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("USR_READ", "REPORT_VIEW"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).contains("USR_READ", "REPORT_VIEW");
        verify(permissionRepository).findPermissionNamesByRoleName(ROLE);
        verify(setOps).add(eq(CACHE_KEY), any(String[].class));
        verify(redisTemplate).expire(eq(CACHE_KEY), eq(Duration.ofMinutes(5)));
    }

    @Test
    void getPermissionsForRoles_cacheEmptySet_treatsAssMissAndLoadsFromDb() {
        when(setOps.members(CACHE_KEY)).thenReturn(Set.of());
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("USR_READ"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).contains("USR_READ");
        verify(permissionRepository).findPermissionNamesByRoleName(ROLE);
    }

    // ── _ALL expansion ───────────────────────────────────────────────────────

    @Test
    void getPermissionsForRoles_formAllExpansion_includesAllFormSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("FORM_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "FORM_READ", "FORM_CREATE", "FORM_UPDATE", "FORM_DELETE",
                "FORM_ASSIGN", "FORM_PUBLISH", "FORM_SESSION_READ", "FORM_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_usrAllExpansion_includesAllUsrSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("USR_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "USR_READ", "USR_CREATE", "USR_UPDATE", "USR_DELETE",
                "USR_ROLE_ASSIGN", "USR_IMPORT", "USR_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_assessAllExpansion_includesAllAssessSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("ASSESS_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "ASSESS_READ", "ASSESS_CREATE", "ASSESS_UPDATE", "ASSESS_DELETE",
                "ASSESS_ASSIGN", "ASSESS_KEY_MANAGE", "ASSESS_RESULT_READ",
                "ASSESS_COMPETENCY_MANAGE", "ASSESS_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_roleAllExpansion_includesAllRoleSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("ROLE_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "ROLE_READ", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE",
                "ROLE_ASSIGN_APPROVE", "ROLE_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_sparkAllExpansion_includesAllSparkSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("SPARK_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "SPARK_NOMINATE", "SPARK_VOTE", "SPARK_REVIEW", "SPARK_MANAGE", "SPARK_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_scopeAllExpansion_includesAllScopeSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("SCOPE_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "SCOPE_TEAM", "SCOPE_ENTITY", "SCOPE_ORG_WIDE", "SCOPE_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_sysAllExpansion_includesAllSysSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("SYS_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of(
                "SYS_AUDIT_VIEW", "SYS_APPROVE", "SYS_ALL"
        ));
    }

    @Test
    void getPermissionsForRoles_aiAllExpansion_includesAllAiSubPermissions() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("AI_ALL"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).containsAll(List.of("AI_USE", "AI_ALL"));
        // AI_ALL is the only group entry — no other AI_* perms should bleed in
        assertThat(result).hasSize(2);
    }

    @Test
    void getPermissionsForRoles_nonAllPermission_notExpanded() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of("USR_READ"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        // Only the single granted permission — no expansion
        assertThat(result).containsExactlyInAnyOrder("USR_READ");
        assertThat(result).doesNotContain("USR_CREATE", "USR_UPDATE", "USR_DELETE");
    }

    // ── Multiple roles ───────────────────────────────────────────────────────

    @Test
    void getPermissionsForRoles_multipleRoles_unionsPermissions() {
        String role2 = "MANAGER";
        String cacheKey2 = "role:permissions:" + role2;

        when(setOps.members(CACHE_KEY)).thenReturn(Set.of("USR_READ"));
        when(setOps.members(cacheKey2)).thenReturn(Set.of("REPORT_VIEW"));

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE, role2));

        assertThat(result).containsExactlyInAnyOrder("USR_READ", "REPORT_VIEW");
    }

    @Test
    void getPermissionsForRoles_emptyList_returnsEmpty() {
        Set<String> result = service.getPermissionsForRoles(List.of());
        assertThat(result).isEmpty();
        verifyNoInteractions(permissionRepository, setOps);
    }

    @Test
    void getPermissionsForRoles_nullList_returnsEmpty() {
        Set<String> result = service.getPermissionsForRoles(null);
        assertThat(result).isEmpty();
    }

    @Test
    void getPermissionsForRoles_roleWithNoDbPermissions_returnsEmpty() {
        when(setOps.members(CACHE_KEY)).thenReturn(null);
        when(permissionRepository.findPermissionNamesByRoleName(ROLE))
                .thenReturn(Set.of());

        Set<String> result = service.getPermissionsForRoles(List.of(ROLE));

        assertThat(result).isEmpty();
        // Empty DB result must NOT be cached (no add() call)
        verify(setOps, never()).add(any(), any(String[].class));
    }

    // ── evictRole ─────────────────────────────────────────────────────────────

    @Test
    void evictRole_deletesCorrectCacheKey() {
        service.evictRole(ROLE);
        verify(redisTemplate).delete(CACHE_KEY);
    }

    // ── evictCache ────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void evictCache_deletesAllRolePermissionKeys() {
        List<String> matchingKeys = List.of(
                "role:permissions:HR_FULL_CRUD",
                "role:permissions:MANAGER",
                "role:permissions:EMPLOYEE"
        );
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        doAnswer(inv -> {
            Consumer<String> consumer = inv.getArgument(0);
            matchingKeys.forEach(consumer);
            return null;
        }).when(scanCursor).forEachRemaining(any());

        service.evictCache();

        verify(redisTemplate).delete(matchingKeys);
    }

    @Test
    void evictCache_noKeys_doesNotCallDelete() {
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(scanCursor);
        // forEachRemaining does nothing — keys list stays empty

        service.evictCache();

        verify(redisTemplate, never()).delete(anyCollection());
    }
}
