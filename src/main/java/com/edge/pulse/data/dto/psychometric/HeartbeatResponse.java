package com.edge.pulse.data.dto.psychometric;

/**
 * Response for {@code GET /api/psychometric/sessions/{id}/time}.
 *
 * <p>{@code remainingSeconds} is {@code null} for untimed sessions.
 * A value of {@code 0} means the time limit has been reached.
 */
public record HeartbeatResponse(Long remainingSeconds) {}
