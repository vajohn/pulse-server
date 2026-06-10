package com.edge.pulse.data.dto;

import com.edge.pulse.data.enums.RoleChangeAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRoleChangeRequest(
    @NotBlank String roleName,
    @NotNull RoleChangeAction action
) {}
