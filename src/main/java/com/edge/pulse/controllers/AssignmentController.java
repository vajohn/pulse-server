package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AssignmentDto;
import com.edge.pulse.data.dto.CreateAssignmentRequest;
import com.edge.pulse.data.dto.MyAssignmentDto;
import com.edge.pulse.data.enums.FormType;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.mappers.AssignmentMapper;
import com.edge.pulse.services.AssignmentService;
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
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentMapper assignmentMapper;

    @PostMapping
    @PreAuthorize("hasAuthority('FORM_ASSIGN')")
    public ResponseEntity<AssignmentDto> createAssignment(
            @RequestBody @Valid CreateAssignmentRequest request,
            Authentication authentication) {
        UUID assignedById = (UUID) authentication.getPrincipal();
        FormAssignment assignment = assignmentService.createAssignment(request, assignedById);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentMapper.toDto(assignment));
    }

    @GetMapping("/form/{formId}")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<List<AssignmentDto>> getAssignmentsForForm(
            @PathVariable UUID formId) {
        return ResponseEntity.ok(assignmentService.getAssignmentsForForm(formId));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<MyAssignmentDto>> getMyAssignments(
            @RequestParam(required = false) FormType formType,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(assignmentService.getMyAssignments(userId, formType));
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<Void> deactivateAssignment(@PathVariable UUID assignmentId) {
        assignmentService.deactivateAssignment(assignmentId);
        return ResponseEntity.noContent().build();
    }
}
