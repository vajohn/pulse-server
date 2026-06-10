package com.edge.pulse.data.dto;

import java.util.UUID;

/**
 * Minimal role projection returned by GET /api/admin/roles/assignable.
 *
 * <p>Intentionally omits {@code permissions} and {@code userCount} — callers of this
 * endpoint (users with {@code USR_ROLE_ASSIGN}) only need to know what roles exist so
 * they can assign one to a user. Exposing permission details or headcount would require
 * {@code ROLE_READ} and is outside the scope of user administration.
 */
public record AssignableRoleDto(UUID id, String name) {}
