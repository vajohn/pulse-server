package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.UserSummary;
import com.edge.pulse.data.models.Role;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.PermissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.core.ScanOptions;

import java.time.Duration;
import java.util.*;

/**
 * Resolves and caches the effective permission set for a given role name.
 *
 * <p>Permissions are resolved from the database (role_permissions join) on cache miss
 * and expanded in-memory using the {@code _ALL} convenience permission rules.
 * The cache TTL is configurable via {@code PERMISSION_TTL_MINUTES} (default 5 min),
 * validated at startup by {@link com.edge.pulse.configs.CacheTtlProperties}.
 */
@Service
@Slf4j
public class PermissionCacheService {

    private final StringRedisTemplate redisTemplate;
    private final PermissionRepository permissionRepository;
    private final Duration ttl;
    private static final String CACHE_KEY_PREFIX = "role:permissions:";

    // _ALL convenience expansion — a role that holds X_ALL automatically receives all X_* sub-permissions.
    // Every permission group now has an _ALL entry (11 groups total).
    // Map.ofEntries is used instead of Map.of because Map.of only supports up to 10 entries.
    private static final Map<String, List<String>> ALL_EXPANSIONS = Map.ofEntries(
        Map.entry("USR_ALL",    List.of("USR_READ","USR_CREATE","USR_UPDATE","USR_DELETE","USR_ROLE_ASSIGN","USR_IMPORT","USR_ALL")),
        Map.entry("ORG_ALL",    List.of("ORG_READ","ORG_CREATE","ORG_UPDATE","ORG_DELETE","ORG_MOVE_USER","ORG_ALL")),
        Map.entry("SYNC_ALL",   List.of("SYNC_TRIGGER","SYNC_STATUS","SYNC_ALL")),
        Map.entry("ROLE_ALL",   List.of("ROLE_READ","ROLE_CREATE","ROLE_UPDATE","ROLE_DELETE","ROLE_ASSIGN_APPROVE","ROLE_ALL")),
        Map.entry("SCOPE_ALL",  List.of("SCOPE_TEAM","SCOPE_ENTITY","SCOPE_ORG_WIDE","SCOPE_ALL")),
        Map.entry("FORM_ALL",   List.of("FORM_READ","FORM_CREATE","FORM_UPDATE","FORM_DELETE","FORM_ASSIGN","FORM_PUBLISH","FORM_SESSION_READ","FORM_ALL")),
        Map.entry("ASSESS_ALL", List.of("ASSESS_READ","ASSESS_CREATE","ASSESS_UPDATE","ASSESS_DELETE","ASSESS_ASSIGN","ASSESS_KEY_MANAGE","ASSESS_RESULT_READ","ASSESS_COMPETENCY_MANAGE","ASSESS_ALL")),
        Map.entry("REPORT_ALL", List.of("REPORT_VIEW","REPORT_EXPORT","REPORT_TEXT_VIEW","REPORT_ASSESS_VIEW","REPORT_ALL")),
        Map.entry("SPARK_ALL",  List.of("SPARK_NOMINATE","SPARK_VOTE","SPARK_REVIEW","SPARK_MANAGE","SPARK_ALL")),
        Map.entry("AI_ALL",     List.of("AI_USE","AI_ALL")),
        Map.entry("SYS_ALL",    List.of("SYS_AUDIT_VIEW","SYS_APPROVE","SYS_ALL"))
    );

    public PermissionCacheService(StringRedisTemplate redisTemplate,
                                  PermissionRepository permissionRepository,
                                  CacheTtlProperties cacheTtlProps) {
        this.redisTemplate = redisTemplate;
        this.permissionRepository = permissionRepository;
        this.ttl = cacheTtlProps.permissionTtl();
    }

    /**
     * Returns the union of all permissions (including _ALL expansions) for the given role names.
     * Uses per-role Redis cache; resolves from DB on miss.
     */
    public Set<String> getPermissionsForRoles(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> allPermissions = new LinkedHashSet<>();
        for (String roleName : roleNames) {
            allPermissions.addAll(getPermissionsForRole(roleName));
        }
        return allPermissions;
    }

    private Set<String> getPermissionsForRole(String roleName) {
        String cacheKey = CACHE_KEY_PREFIX + roleName;

        Set<String> cached = redisTemplate.opsForSet().members(cacheKey);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        Set<String> fromDb = permissionRepository.findPermissionNamesByRoleName(roleName);
        if (fromDb.isEmpty()) {
            log.warn("No permissions found in DB for role: {}", roleName);
            return Collections.emptySet();
        }

        Set<String> expanded = expandAllPermissions(fromDb);
        redisTemplate.opsForSet().add(cacheKey, expanded.toArray(new String[0]));
        redisTemplate.expire(cacheKey, ttl);
        return expanded;
    }

    private Set<String> expandAllPermissions(Set<String> raw) {
        Set<String> result = new HashSet<>(raw);
        for (String perm : raw) {
            List<String> expansion = ALL_EXPANSIONS.get(perm);
            if (expansion != null) {
                result.addAll(expansion);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Evicts the cached permission set for the given role.
     * Must be called after any role-permission mutation via AdminRoleController.
     */
    public void evictRole(String roleName) {
        redisTemplate.delete(CACHE_KEY_PREFIX + roleName);
        log.debug("Permission cache evicted for role: {}", roleName);
    }

    /**
     * Evicts all cached role-permission mappings.
     * Called by EnumSyncService after a full permission sync.
     *
     * <p>Uses SCAN instead of KEYS to avoid blocking the Redis server on large keyspaces.
     */
    public void evictCache() {
        List<String> keys = new ArrayList<>();
        try (var cursor = redisTemplate.scan(
                ScanOptions.scanOptions()
                        .match(CACHE_KEY_PREFIX + "*")
                        .count(100)
                        .build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        log.info("Permission cache evicted (all roles, {} keys)", keys.size());
    }

    @NonNull
    public UserSummary toUserSummary(User user) {
        List<String> roleNames = user.getRoles() != null
                ? new ArrayList<>(user.getRoles().stream().map(Role::getName).toList())
                : Collections.emptyList();
        List<String> permissions = new ArrayList<>(getPermissionsForRoles(roleNames));
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getDepartment(),
                roleNames,
                permissions,
                user.getOrgUnit() != null ? user.getOrgUnit().getId() : null,
                user.getOrgUnit() != null ? user.getOrgUnit().getOrgUnitName() : null
        );
    }
}
