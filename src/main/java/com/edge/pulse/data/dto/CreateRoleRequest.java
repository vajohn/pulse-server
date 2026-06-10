package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/admin/roles.
 *
 * <p>Role names must be uppercase with underscores only to match the existing naming convention.
 */
public record CreateRoleRequest(
        @NotBlank(message = "Role name is required")
        @Size(min = 2, max = 64, message = "Role name must be between 2 and 64 characters")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Role name must be uppercase letters, digits, and underscores")
        String name
) {}
