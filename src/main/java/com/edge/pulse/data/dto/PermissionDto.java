package com.edge.pulse.data.dto;

/**
 * Permission detail included in {@link AdminRoleDto}.
 *
 * <p>{@code group} is derived from the permission name prefix (e.g. {@code "USR_READ"} → {@code "USR"}).
 * Flutter uses it to drive accordion grouping in AdminRolesScreen without a second API call.
 */
public record PermissionDto(
        String name,
        String description,
        String group
) {
    /** Derives the group prefix from the permission name (everything before the first '_'). */
    public static String groupOf(String permissionName) {
        int idx = permissionName.indexOf('_');
        return idx > 0 ? permissionName.substring(0, idx) : permissionName;
    }
}
