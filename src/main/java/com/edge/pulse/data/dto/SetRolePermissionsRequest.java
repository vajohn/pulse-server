package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Request body for PUT /api/admin/roles/{id}/permissions.
 *
 * <p>The {@code permissions} set replaces the role's full permission set atomically.
 * SCOPE_* permissions in this set require the caller to hold ROLE_ALL authority —
 * validated in {@code RoleManagementService.setPermissions()}.
 */
public record SetRolePermissionsRequest(
        @NotNull(message = "permissions must not be null (use an empty set to clear all)")
        Set<String> permissions
) {}
