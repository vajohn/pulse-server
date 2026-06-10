package com.edge.pulse.data.dto;
import java.time.LocalDateTime;
import java.util.UUID;

public record AnonIdentityDto(
    UUID id,
    String token,
    LocalDateTime windowStart,
    LocalDateTime windowEnd,
    int sequenceInWindow
) {}
