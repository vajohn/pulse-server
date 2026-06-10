package com.edge.pulse.services;

import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.repositories.OrganizationalUnitRepository;
import com.edge.pulse.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves data visibility scope from the caller's SCOPE_* permissions.
 *
 * <p>Four-branch resolution order:
 * <ol>
 *   <li>{@code SCOPE_ORG_WIDE} — all active org units</li>
 *   <li>{@code SCOPE_ENTITY} — org units sharing the caller's company_code</li>
 *   <li>{@code SCOPE_TEAM} — caller's org unit subtree</li>
 *   <li>No scope — caller's own org unit only</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class OrgUnitScopeService {
    private final UserRepository userRepository;
    private final OrganizationalUnitRepository orgUnitRepository;

    // ── Primary scope resolution API ─────────────────────────────────────────

    /**
     * Returns the list of accessible org unit IDs for the given user, resolved
     * from the provided authority strings (from {@code Authentication.getAuthorities()}).
     */
    @Transactional(readOnly = true)
    public List<UUID> resolveAccessibleOrgUnitIds(UUID userId, Collection<String> authorities) {
        if (authorities.contains("SCOPE_ORG_WIDE")) {
            return orgUnitRepository.findAllActiveIds();
        }
        if (authorities.contains("SCOPE_ENTITY")) {
            String companyCode = userRepository.findCompanyCodeById(userId).orElse(null);
            if (companyCode != null) {
                return orgUnitRepository.findAllActiveIdsByCompanyCode(companyCode);
            }
            // No company_code yet (pre-SAF-sync) — fall through to SCOPE_TEAM
        }
        if (authorities.contains("SCOPE_TEAM")) {
            Optional<String> pathPrefix = orgUnitRepository.findPathPrefixByUserId(userId);
            Optional<UUID> ownId = orgUnitRepository.findOrgUnitIdByUserId(userId);
            if (pathPrefix.isPresent() && ownId.isPresent()) {
                return orgUnitRepository.findAllActiveIdsByPathPrefix(pathPrefix.get(), ownId.get());
            }
        }
        // Own data only
        return orgUnitRepository.findOrgUnitIdByUserId(userId)
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    /**
     * Returns a set of accessible org unit IDs using authorities from the current
     * SecurityContext. Convenience wrapper for service-layer callers that already
     * have access to the SecurityContext.
     */
    @Transactional(readOnly = true)
    public Set<UUID> resolveAccessibleOrgUnitIdsFromContext(UUID userId) {
        Collection<String> authorities = extractAuthoritiesFromContext();
        return new HashSet<>(resolveAccessibleOrgUnitIds(userId, authorities));
    }

    /**
     * Returns {@code true} if the caller has any org-wide or entity-wide scope authority.
     * Used by AnalyticsService to decide whether to apply org unit filtering.
     */
    public boolean hasBroadScope(Collection<String> authorities) {
        return authorities.contains("SCOPE_ORG_WIDE") || authorities.contains("SCOPE_ENTITY");
    }

    // ── Legacy compatibility helpers (SecurityContext-based) ─────────────────

    /**
     * Returns true if the authenticated user can access the target user's data,
     * based on their SCOPE_* permissions.
     */
    @Transactional(readOnly = true)
    public boolean canAccess(UUID authenticatedUserId, UUID targetUserId) {
        Collection<String> authorities = extractAuthoritiesFromContext();

        // SCOPE_ORG_WIDE can access anyone
        if (authorities.contains("SCOPE_ORG_WIDE")) return true;

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Target user not found"));
        if (targetUser.getOrgUnit() == null) return false;

        Set<UUID> accessibleIds = new HashSet<>(resolveAccessibleOrgUnitIds(authenticatedUserId, authorities));
        return accessibleIds.contains(targetUser.getOrgUnit().getId());
    }

    /**
     * Filters a list of users to only those within the authenticated caller's scope.
     */
    @Transactional(readOnly = true)
    public List<User> filterByScope(UUID authenticatedUserId, List<User> users) {
        Collection<String> authorities = extractAuthoritiesFromContext();

        if (authorities.contains("SCOPE_ORG_WIDE")) return users;

        Set<UUID> accessibleIds = new HashSet<>(resolveAccessibleOrgUnitIds(authenticatedUserId, authorities));
        return users.stream()
                .filter(u -> u.getOrgUnit() != null && accessibleIds.contains(u.getOrgUnit().getId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the set of org unit IDs that a team-scoped user can access
     * (their own org unit + all descendants via path prefix).
     * Preserved for callers that need the managed ID set directly.
     */
    @Transactional(readOnly = true)
    public Set<UUID> getManagedOrgUnitIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getOrgUnit() == null) return Collections.emptySet();

        OrganizationalUnit orgUnit = user.getOrgUnit();
        String pathPrefix = orgUnit.getPath().isEmpty()
                ? orgUnit.getId().toString()
                : orgUnit.getPath() + "/" + orgUnit.getId();

        Set<UUID> managedIds = new HashSet<>();
        managedIds.add(orgUnit.getId());
        orgUnitRepository.findByPathPrefix(pathPrefix)
                .forEach(ou -> managedIds.add(ou.getId()));
        return managedIds;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private Collection<String> extractAuthoritiesFromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Collections.emptySet();
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
    }
}
