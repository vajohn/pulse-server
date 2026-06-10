package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.RoleChangeStatus;
import jakarta.validation.constraints.NotNull;

public record ReviewRoleChangeRequest(
    @NotNull RoleChangeStatus status,
    String comment
) {}
