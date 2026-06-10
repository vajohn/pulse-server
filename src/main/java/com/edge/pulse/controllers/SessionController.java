package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.dto.BatchSubmitRequest;
import com.edge.pulse.data.dto.SessionDto;
import com.edge.pulse.data.dto.SubmitAnswerRequest;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.mappers.AnswerMapper;
import com.edge.pulse.mappers.SessionMapper;
import com.edge.pulse.services.AnswerService;
import com.edge.pulse.services.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AnswerService answerService;
    private final SessionMapper sessionMapper;

    @GetMapping("/{id}/prior-answers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AnswerDto>> getPriorAnswers(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        ResponseSession session = sessionService.getSession(id, userId);
        if (session.isAnonymous()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<AnswerDto> priorAnswers = answerService.getPriorAnswers(id);
        return ResponseEntity.ok(priorAnswers);
    }

    @PostMapping("/{id}/answers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnswerDto> submitAnswer(@PathVariable UUID id,
                                                   @Valid @RequestBody SubmitAnswerRequest request,
                                                   Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        sessionService.getSession(id, userId);
        AnswerDto answer = answerService.submitAnswer(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(answer);
    }

    @PutMapping("/{id}/answers/{questionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AnswerDto> updateAnswer(@PathVariable UUID id,
                                                   @PathVariable UUID questionId,
                                                   @Valid @RequestBody SubmitAnswerRequest request,
                                                   Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        sessionService.getSession(id, userId);
        AnswerDto answer = answerService.versionAnswer(id, questionId, request);
        return ResponseEntity.ok(answer);
    }

    @GetMapping("/{id}/answers/{questionId}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AnswerDto>> getAnswerHistory(@PathVariable UUID id,
                                                             @PathVariable UUID questionId,
                                                             Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        ResponseSession session = sessionService.getSession(id, userId);
        if (session.isAnonymous()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<AnswerDto> history = answerService.getAnswerHistory(id, questionId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/{id}/submit-and-complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionDto> submitAndComplete(@PathVariable UUID id,
                                                         @Valid @RequestBody BatchSubmitRequest request,
                                                         Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        answerService.submitAll(id, request.answers());
        ResponseSession session = sessionService.completeSession(id, userId);
        List<AnswerDto> currentAnswers = answerService.getCurrentAnswers(id);
        return ResponseEntity.ok(sessionMapper.toDto(session, currentAnswers));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SessionDto> completeSession(@PathVariable UUID id, Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        ResponseSession session = sessionService.completeSession(id, userId);
        List<AnswerDto> currentAnswers = answerService.getCurrentAnswers(id);
        return ResponseEntity.ok(sessionMapper.toDto(session, currentAnswers));
    }
}
