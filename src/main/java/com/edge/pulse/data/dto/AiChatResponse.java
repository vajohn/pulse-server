package com.edge.pulse.data.dto;

/**
 * Response body for {@code POST /api/ai/chat}.
 *
 * @param reply The AI-generated reply text.
 */
public record AiChatResponse(String reply) {}
