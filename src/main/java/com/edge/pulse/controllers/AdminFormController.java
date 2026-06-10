package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.*;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.services.AuditService;
import com.edge.pulse.services.SurveyService;
import com.edge.pulse.mappers.FormMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/forms")
@RequiredArgsConstructor
public class AdminFormController {
    private final SurveyService surveyService;
    private final FormMapper formMapper;
    private final AuditService auditService;

    @GetMapping
    @PreAuthorize("hasAuthority('FORM_READ')")
    public ResponseEntity<List<FormDto>> listForms() {
        List<FormDto> result = surveyService.listForms().stream()
                .map(formMapper::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_READ')")
    public ResponseEntity<FormDto> getForm(@PathVariable UUID id) {
        Form form = surveyService.getForm(id);
        return ResponseEntity.ok(formMapper.toDto(form));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FORM_CREATE')")
    public ResponseEntity<FormDto> createForm(
            @RequestBody @Valid CreateFormRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        Form form = surveyService.createForm(request);
        auditService.logAction(authUserId, "FORM_CREATE", "SURVEY", form.getId(),
                auditService.buildDetail("title", form.getTitle()), null);
        return ResponseEntity.ok(formMapper.toDto(form));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<FormDto> updateForm(
            @PathVariable UUID id,
            @RequestBody @Valid CreateFormRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        Form form = surveyService.updateForm(id, request);
        auditService.logAction(authUserId, "FORM_UPDATE", "SURVEY", id, null, null);
        return ResponseEntity.ok(formMapper.toDto(form));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_DELETE')")
    public ResponseEntity<Void> deleteForm(@PathVariable UUID id, Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        surveyService.deleteForm(id);
        auditService.logAction(authUserId, "FORM_DELETE", "SURVEY", id, null, null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<List<QuestionDto>> getAllQuestions(@PathVariable UUID id) {
        List<Question> all = surveyService.getAllQuestions(id);
        return ResponseEntity.ok(all.stream().map(formMapper::toQuestionDto).toList());
    }

    @PostMapping("/{id}/questions")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<QuestionDto> addQuestion(
            @PathVariable UUID id,
            @RequestBody @Valid AddQuestionRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        Question question = surveyService.addQuestion(id, request);
        auditService.logAction(authUserId, "QUESTION_ADD", "SURVEY", id, null, null);
        return ResponseEntity.ok(formMapper.toQuestionDto(question));
    }

    @PutMapping("/{id}/questions/{qId}")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<QuestionDto> updateQuestion(
            @PathVariable UUID id,
            @PathVariable UUID qId,
            @RequestBody @Valid UpdateQuestionRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        Question question = surveyService.updateQuestion(id, qId, request);
        auditService.logAction(authUserId, "QUESTION_UPDATE", "QUESTION", qId, null, null);
        return ResponseEntity.ok(formMapper.toQuestionDto(question));
    }

    @DeleteMapping("/{id}/questions/{qId}")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable UUID id,
            @PathVariable UUID qId,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        surveyService.expireQuestion(id, qId);
        auditService.logAction(authUserId, "QUESTION_DELETE", "QUESTION", qId, null, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/questions/{qId}/permanent")
    @PreAuthorize("hasAuthority('FORM_UPDATE')")
    public ResponseEntity<Void> permanentlyDeleteQuestion(
            @PathVariable UUID id,
            @PathVariable UUID qId,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        surveyService.hardDeleteQuestion(id, qId);
        auditService.logAction(authUserId, "QUESTION_HARD_DELETE", "QUESTION", qId, null, null);
        return ResponseEntity.noContent().build();
    }
}
