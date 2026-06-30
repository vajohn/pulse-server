package com.edge.pulse.data.dto;

import java.util.List;

/**
 * Optional server-side filters for the admin users list.
 *
 * <p>Every field is optional: a null/blank/empty value means "do not filter on this dimension".
 * Filtering is applied in-memory in {@link com.edge.pulse.services.UserService} after the scoped
 * user set has been materialised.
 *
 * @param q             free-text match against displayName or email (case-insensitive, capped at 50 chars)
 * @param roleNames     keep users holding at least one of these role names (OR-within)
 * @param noRoles       keep only users with no roles assigned
 * @param permission    keep only users whose effective (expanded) permission set contains this permission
 * @param status        "active" / "inactive" (case-insensitive); anything else is a no-op
 * @param neverLoggedIn keep only users that have never logged in (lastLoginAt == null)
 * @param staleDays     keep users that never logged in OR last logged in before now - staleDays
 * @param syncSource    "SAF" (provisioned from SAF, sfUserId != null) / "X4AUTH" (sfUserId == null); else no-op
 */
public record UserFilter(
        String q,
        List<String> roleNames,
        boolean noRoles,
        String permission,
        String status,
        boolean neverLoggedIn,
        Integer staleDays,
        String syncSource) {

    public UserFilter {
        roleNames = roleNames == null ? List.of() : roleNames;
        // A non-positive staleDays would make the cutoff (now - staleDays) be now/future,
        // matching essentially everyone and inverting the "stale" intent — treat as absent.
        if (staleDays != null && staleDays <= 0) staleDays = null;
    }

    public static UserFilter empty() {
        return new UserFilter(null, List.of(), false, null, null, false, null, null);
    }
}
