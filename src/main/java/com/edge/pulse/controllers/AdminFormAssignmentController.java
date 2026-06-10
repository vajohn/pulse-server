package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.AssignmentDto;
import com.edge.pulse.data.dto.CreateAssignmentRequest;
import com.edge.pulse.data.dto.UpdateFormAssignmentRequest;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.mappers.AssignmentMapper;
import com.edge.pulse.services.AssignmentService;
import com.edge.pulse.services.AuditService;
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
@RequestMapping("/api/admin/form-assignments")
@RequiredArgsConstructor
public class AdminFormAssignmentController {

    private final AssignmentService assignmentService;
    private final AuditService auditService;
    private final AssignmentMapper assignmentMapper;

    @GetMapping
    @PreAuthorize("hasAuthority('FORM_ASSIGN')")
    public ResponseEntity<List<AssignmentDto>> listAssignments(
            @RequestParam(required = false) UUID formId,
            @RequestParam(required = false, defaultValue = "false") boolean archived) {
        if (archived) {
            return ResponseEntity.ok(assignmentService.getArchivedAssignments());
        }
        if (formId != null) {
            return ResponseEntity.ok(assignmentService.getAssignmentsForForm(formId));
        }
        return ResponseEntity.ok(assignmentService.getAllActiveAssignments());
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('FORM_ASSIGN')")
    public ResponseEntity<AssignmentDto> createAssignment(
            @RequestBody @Valid CreateAssignmentRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        FormAssignment assignment = assignmentService.createAssignment(request, authUserId);
        auditService.logAction(authUserId, "ASSIGNMENT_CREATE", "FORM_ASSIGNMENT", assignment.getId(), null, null);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentMapper.toDto(assignment));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<Void> archiveAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        assignmentService.deactivateAssignment(id);
        auditService.logAction(authUserId, "ASSIGNMENT_ARCHIVE", "FORM_ASSIGNMENT", id, null, null);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<AssignmentDto> updateAssignment(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateFormAssignmentRequest request,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        AssignmentDto dto = assignmentService.updateAssignment(id, request);
        auditService.logAction(authUserId, "ASSIGNMENT_UPDATE", "FORM_ASSIGNMENT", id, null, null);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<Void> restoreAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        assignmentService.reactivateAssignment(id);
        auditService.logAction(authUserId, "ASSIGNMENT_RESTORE", "FORM_ASSIGNMENT", id, null, null);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FORM_PUBLISH')")
    public ResponseEntity<Void> deleteAssignment(
            @PathVariable UUID id,
            Authentication auth) {
        UUID authUserId = (UUID) auth.getPrincipal();
        assignmentService.hardDeleteAssignment(id);
        auditService.logAction(authUserId, "ASSIGNMENT_DELETE", "FORM_ASSIGNMENT", id, null, null);
        return ResponseEntity.noContent().build();
    }
}
