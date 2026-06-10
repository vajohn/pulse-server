package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AiChatRequest;
import com.edge.pulse.data.dto.AiChatResponse;
import com.edge.pulse.services.FalconService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * AI feature endpoints — currently proxies to the Falcon LLM API.
 *
 * <p>{@code POST /api/ai/chat} returns HTTP 503 when {@code FALCON_API_KEY}
 * is not set, so clients can gracefully hide AI UI in that case.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final FalconService falconService;

    /**
     * Returns whether AI features are available on this server instance.
     *
     * <p>Flutter uses this at startup to show or hide AI UI without needing
     * to attempt a chat call first.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('AI_USE')")
    public ResponseEntity<AiStatusResponse> status() {
        return ResponseEntity.ok(new AiStatusResponse(falconService.isEnabled()));
    }

    private record AiStatusResponse(boolean enabled) {}

    /**
     * Forwards a chat message to Falcon LLM and streams the reply back.
     *
     * <p>Returns 503 when the server-side API key is not configured.
     */
    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('AI_USE')")
    public ResponseEntity<AiChatResponse> chat(@RequestBody @Valid AiChatRequest request) {
        if (!falconService.isEnabled()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AiChatResponse("AI features are not enabled on this server."));
        }
        String reply = falconService.chat(request.message(), request.systemPrompt());
        return ResponseEntity.ok(new AiChatResponse(reply));
    }
}
