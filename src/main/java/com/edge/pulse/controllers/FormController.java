package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AnswerDto;
import com.edge.pulse.data.dto.FormDto;
import com.edge.pulse.data.dto.OpenSessionRequest;
import com.edge.pulse.data.dto.SessionDto;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.mappers.FormMapper;
import com.edge.pulse.mappers.SessionMapper;
import com.edge.pulse.services.AnswerService;
import com.edge.pulse.services.SessionService;
import com.edge.pulse.services.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final SurveyService surveyService;
    private final SessionService sessionService;
    private final AnswerService answerService;
    private final FormMapper formMapper;
    private final SessionMapper sessionMapper;

    @GetMapping("/{id}")
    public ResponseEntity<FormDto> getForm(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        Form form = surveyService.getFormForUser(id, userId);
        return ResponseEntity.ok(formMapper.toDto(form, surveyService.getActiveQuestions(id)));
    }

    @PostMapping("/{id}/sessions")
    public ResponseEntity<SessionDto> openSession(@PathVariable UUID id,
                                                   @RequestBody OpenSessionRequest request,
                                                   Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        ResponseSession session = sessionService.openOrResumeSession(id, userId, request);
        List<AnswerDto> currentAnswers = answerService.getCurrentAnswers(session.getId());
        return ResponseEntity.ok(sessionMapper.toDto(session, currentAnswers));
    }

    @GetMapping("/{id}/rollup")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<Void> getRollup(@PathVariable UUID id) {
        // Placeholder — Phase 6 (Analytics Rebuild)
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
