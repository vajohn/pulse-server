package com.edge.pulse.data.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/ai/chat}.
 *
 * @param message      The user's message (required, max 4 000 chars).
 * @param systemPrompt Optional system-level instruction to shape the AI's persona/tone.
 */
public record AiChatRequest(
        @NotBlank @Size(max = 4000) String message,
        String systemPrompt
) {}
