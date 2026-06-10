package com.edge.pulse.data.dto;

public record UpdateUserRequest(
    String displayName,
    String department,
    String division,
    String costCenter,
    String employeeId
) {}
