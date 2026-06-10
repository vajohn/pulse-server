package com.edge.pulse.data.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UserSummary user
) {}
