package com.edge.pulse.data.dto;

import java.util.List;
import java.util.UUID;

/**
 * Role representation returned by GET /api/admin/roles and PUT /api/admin/roles/{id}/permissions.
 *
 * <p>The {@code group} field on each {@link PermissionDto} is the permission name prefix
 * (e.g. "USR", "FORM") — computed in the mapper from {@code name.split("_")[0]}.
 * Flutter uses this to group permissions into accordion sections without a second API call.
 */
public record AdminRoleDto(
        UUID id,
        String name,
        List<PermissionDto> permissions,
        int userCount
) {}
