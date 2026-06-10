package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.AssignmentDto;
import com.edge.pulse.data.dto.CreateAssignmentRequest;
import com.edge.pulse.data.dto.MyAssignmentDto;
import com.edge.pulse.data.dto.UpdateFormAssignmentRequest;
import com.edge.pulse.data.enums.AssignmentStatus;
import com.edge.pulse.data.enums.FormType;
import com.edge.pulse.data.models.*;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.mappers.AssignmentMapper;
import com.edge.pulse.repositories.*;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AssignmentService {

    private final FormAssignmentRepository assignmentRepository;
    private final FormRepository formRepository;
    private final OrganizationalUnitRepository orgUnitRepository;
    private final UserRepository userRepository;
    private final ResponseSessionRepository responseSessionRepository;
    private final AnswerSubmissionRepository answerSubmissionRepository;
    private final QuestionRepository questionRepository;
    private final FormCacheService cacheService;
    private final AssignmentMapper assignmentMapper;
    private final PsychometricTestRepository psychometricTestRepository;
    private final AuditService auditService;
    private final CacheTtlProperties cacheTtlProps;

    public FormAssignment createAssignment(CreateAssignmentRequest request, UUID assignedById) {
        if (request.orgUnitId() != null && request.userId() != null) {
            throw new IllegalArgumentException("Assignment cannot target both an org unit and a user");
        }
        if (request.orgUnitId() == null && request.userId() == null) {
            throw new IllegalArgumentException("Assignment must target either an org unit or a user");
        }

        Form form = formRepository.findById(request.formId())
                .orElseThrow(() -> new IllegalArgumentException("Form not found: " + request.formId()));

        // For PSYCHOMETRIC forms the meaningful unit of content is scales, not survey questions.
        long assignableCount = form.getFormType() == FormType.PSYCHOMETRIC
                ? psychometricTestRepository.countScalesByFormId(request.formId())
                : formRepository.countActiveQuestionsByFormId(request.formId());
        if (assignableCount == 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot assign a form with no content");
        }

        User assignedBy = userRepository.findById(assignedById)
                .orElseThrow(() -> new IllegalArgumentException("Assigning user not found: " + assignedById));

        FormAssignment.FormAssignmentBuilder builder = FormAssignment.builder()
                .form(form)
                .assignedBy(assignedBy)
                .startsAt(request.startsAt())
                .expiresAt(request.expiresAt())
                .dueDate(request.dueDate())
                .mandatory(request.mandatory())
                .includeChildren(request.includeChildren())
                .allowResubmission(request.allowResubmission());

        if (request.orgUnitId() != null) {
            OrganizationalUnit orgUnit = orgUnitRepository.findById(request.orgUnitId())
                    .orElseThrow(() -> new IllegalArgumentException("Org unit not found: " + request.orgUnitId()));

            if (assignmentRepository.existsByFormIdAndOrgUnitIdAndActiveTrue(
                    request.formId(), request.orgUnitId())) {
                throw new IllegalStateException("Form already assigned to this org unit");
            }

            builder.orgUnit(orgUnit);
        } else {
            User targetUser = userRepository.findById(request.userId())
                    .orElseThrow(() -> new IllegalArgumentException("Target user not found: " + request.userId()));

            if (assignmentRepository.existsByFormIdAndUserIdAndActiveTrue(
                    request.formId(), request.userId())) {
                throw new IllegalStateException("Form already assigned to this user");
            }

            builder.user(targetUser);
        }

        FormAssignment saved = assignmentRepository.save(builder.build());

        auditService.logAction(assignedById, "FORM_ASSIGNMENT_CREATED", "FormAssignment",
                saved.getId(),
                auditService.buildDetail(
                        "formId", request.formId(),
                        "orgUnitId", request.orgUnitId(),
                        "userId", request.userId()),
                null);

        // Evict assignment caches for affected users.
        // For org-unit assignments we do not SCAN all user keys — the TTL provides
        // a bounded stale window. Org assignments change only on admin action and
        // user impact is bounded to cacheTtlProps.assignmentTtlMinutes.
        if (request.userId() != null) {
            cacheService.evict(FormCacheService.userAssignmentsKey(request.userId()));
        } else {
            log.debug("Org-unit assignment created; user caches will expire within {} min",
                    cacheTtlProps.getAssignmentTtlMinutes());
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> getAssignmentsForForm(UUID formId) {
        return assignmentRepository.findByFormIdAndActiveTrue(formId)
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    /**
     * Returns the user's visible assignment list, optionally filtered by form type.
     *
     * <p>When {@code formType} is null all assignments are returned and the result is
     * cached (10-min TTL). When a type filter is provided the cache is bypassed so that
     * each tab (Surveys / Tests) always gets a fresh, narrowly-scoped list.
     */
    @Transactional(readOnly = true)
    public List<MyAssignmentDto> getMyAssignments(UUID userId, FormType formType) {
        if (formType != null) {
            return getMyAssignmentsFiltered(userId, formType);
        }
        return getMyAssignments(userId);
    }

    /**
     * Filtered variant — bypasses the Redis cache.
     */
    @Transactional(readOnly = true)
    private List<MyAssignmentDto> getMyAssignmentsFiltered(UUID userId, FormType formType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";

        List<FormAssignment> assignments = assignmentRepository
                .findVisibleAssignmentsForUserByType(userId, orgPath, formType);
        if (assignments.isEmpty()) {
            return List.of();
        }

        List<UUID> formIds = assignments.stream().map(a -> a.getForm().getId()).distinct().toList();

        List<ResponseSession> allSessions = responseSessionRepository.findByUserIdAndFormIdIn(userId, formIds);
        Map<UUID, ResponseSession> latestByForm = new LinkedHashMap<>();
        for (ResponseSession s : allSessions) {
            latestByForm.putIfAbsent(s.getForm().getId(), s);
        }

        Map<UUID, Integer> questionCountByForm = new HashMap<>();
        for (Object[] row : questionRepository.countActiveByFormIds(formIds)) {
            questionCountByForm.put((UUID) row[0], ((Long) row[1]).intValue());
        }

        List<UUID> inProgressIds = latestByForm.values().stream()
                .filter(s -> s.getCompletedAt() == null)
                .map(ResponseSession::getId)
                .toList();
        Map<UUID, Integer> answeredBySession = new HashMap<>();
        if (!inProgressIds.isEmpty()) {
            for (Object[] row : answerSubmissionRepository.countDistinctQuestionsAnsweredBySessionIds(inProgressIds)) {
                answeredBySession.put((UUID) row[0], ((Long) row[1]).intValue());
            }
        }

        // Batch 4: psychometric test IDs (conditional)
        Map<UUID, UUID> formToTestId = buildFormToTestIdMap(assignments);

        return assignments.stream()
                .map(a -> buildEnrichedDto(a, latestByForm.get(a.getForm().getId()),
                        questionCountByForm.getOrDefault(a.getForm().getId(), 0),
                        answeredBySession, formToTestId))
                .toList();
    }

    /**
     * Returns ALL visible assignments for the user, using the Redis cache.
     */
    @Transactional(readOnly = true)
    public List<MyAssignmentDto> getMyAssignments(UUID userId) {
        String cacheKey = FormCacheService.userAssignmentsKey(userId);

        Optional<List<MyAssignmentDto>> cached = cacheService.get(cacheKey, new TypeReference<>() {});
        if (cached.isPresent()) {
            return cached.get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        String orgPath = user.getOrgUnit() != null ? user.getOrgUnit().getPath() : "";

        List<FormAssignment> assignments = assignmentRepository.findVisibleAssignmentsForUser(userId, orgPath);
        if (assignments.isEmpty()) {
            cacheService.put(cacheKey, List.of(), cacheTtlProps.assignmentTtl());
            return List.of();
        }

        List<UUID> formIds = assignments.stream().map(a -> a.getForm().getId()).distinct().toList();

        // Batch 1: all sessions for this user across all assigned forms
        List<ResponseSession> allSessions = responseSessionRepository.findByUserIdAndFormIdIn(userId, formIds);
        // Take the most-recent session per form (query returns DESC by startedAt)
        Map<UUID, ResponseSession> latestByForm = new LinkedHashMap<>();
        for (ResponseSession s : allSessions) {
            latestByForm.putIfAbsent(s.getForm().getId(), s);
        }

        // Batch 2: active question counts per form
        Map<UUID, Integer> questionCountByForm = new HashMap<>();
        for (Object[] row : questionRepository.countActiveByFormIds(formIds)) {
            questionCountByForm.put((UUID) row[0], ((Long) row[1]).intValue());
        }

        // Batch 3: answered counts for in-progress sessions only
        List<UUID> inProgressIds = latestByForm.values().stream()
                .filter(s -> s.getCompletedAt() == null)
                .map(ResponseSession::getId)
                .toList();
        Map<UUID, Integer> answeredBySession = new HashMap<>();
        if (!inProgressIds.isEmpty()) {
            for (Object[] row : answerSubmissionRepository.countDistinctQuestionsAnsweredBySessionIds(inProgressIds)) {
                answeredBySession.put((UUID) row[0], ((Long) row[1]).intValue());
            }
        }

        // Batch 4: psychometric test IDs (conditional)
        Map<UUID, UUID> formToTestId = buildFormToTestIdMap(assignments);

        List<MyAssignmentDto> result = assignments.stream()
                .map(a -> buildEnrichedDto(a, latestByForm.get(a.getForm().getId()),
                        questionCountByForm.getOrDefault(a.getForm().getId(), 0),
                        answeredBySession, formToTestId))
                .toList();

        cacheService.put(cacheKey, result, cacheTtlProps.assignmentTtl());
        return result;
    }

    public void deactivateAssignment(UUID assignmentId) {
        FormAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        assignment.setActive(false);
        assignmentRepository.save(assignment);

        if (assignment.getUser() != null) {
            cacheService.evict(FormCacheService.userAssignmentsKey(assignment.getUser().getId()));
        } else {
            log.debug("Org-unit assignment deactivated; user caches will expire within {} min",
                    cacheTtlProps.getAssignmentTtlMinutes());
        }
    }

    public AssignmentDto updateAssignment(UUID assignmentId, UpdateFormAssignmentRequest request) {
        FormAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));

        if (request.dueDate() != null) assignment.setDueDate(request.dueDate());
        if (request.startsAt() != null) assignment.setStartsAt(request.startsAt());
        if (request.expiresAt() != null) assignment.setExpiresAt(request.expiresAt());
        if (request.mandatory() != null) assignment.setMandatory(request.mandatory());
        if (request.allowResubmission() != null) assignment.setAllowResubmission(request.allowResubmission());
        assignmentRepository.save(assignment);

        if (assignment.getUser() != null) {
            cacheService.evict(FormCacheService.userAssignmentsKey(assignment.getUser().getId()));
        } else {
            log.debug("Org-unit assignment updated; user caches will expire within {} min",
                    cacheTtlProps.getAssignmentTtlMinutes());
        }

        return assignmentMapper.toDto(assignment);
    }

    public void reactivateAssignment(UUID assignmentId) {
        FormAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        assignment.setActive(true);
        assignmentRepository.save(assignment);

        if (assignment.getUser() != null) {
            cacheService.evict(FormCacheService.userAssignmentsKey(assignment.getUser().getId()));
        } else {
            log.debug("Org-unit assignment reactivated; user caches will expire within {} min",
                    cacheTtlProps.getAssignmentTtlMinutes());
        }
    }

    public void hardDeleteAssignment(UUID assignmentId) {
        FormAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + assignmentId));
        if (assignment.isActive()) {
            throw new IllegalStateException("Cannot permanently delete an active assignment. Archive it first.");
        }
        assignmentRepository.deleteById(assignmentId);
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> getAllActiveAssignments() {
        return assignmentRepository.findByActiveTrueOrderByAssignedAtDesc()
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> getArchivedAssignments() {
        return assignmentRepository.findByActiveFalseOrderByAssignedAtDesc()
                .stream()
                .map(assignmentMapper::toDto)
                .toList();
    }

    private Map<UUID, UUID> buildFormToTestIdMap(List<FormAssignment> assignments) {
        Set<UUID> psychometricFormIds = assignments.stream()
                .filter(a -> a.getForm().getFormType() == FormType.PSYCHOMETRIC)
                .map(a -> a.getForm().getId())
                .collect(Collectors.toSet());
        if (psychometricFormIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return psychometricTestRepository.findByFormIdIn(psychometricFormIds)
                .stream()
                .collect(Collectors.toMap(t -> t.getForm().getId(), PsychometricTest::getId));
    }

    private MyAssignmentDto buildEnrichedDto(FormAssignment assignment,
                                              ResponseSession latestSession,
                                              int totalQuestions,
                                              Map<UUID, Integer> answeredBySession,
                                              Map<UUID, UUID> formToTestId) {
        UUID formId = assignment.getForm().getId();

        AssignmentStatus status;
        UUID sessionId = null;
        LocalDateTime startedAt = null;
        LocalDateTime completedAt = null;
        int answeredCount = 0;

        if (latestSession != null) {
            sessionId = latestSession.getId();
            startedAt = latestSession.getStartedAt();
            completedAt = latestSession.getCompletedAt();
            if (latestSession.getCompletedAt() == null) {
                answeredCount = answeredBySession.getOrDefault(latestSession.getId(), 0);
            }
            if (latestSession.getCompletedAt() != null) {
                status = assignment.isAllowResubmission()
                        ? AssignmentStatus.RETAKEABLE
                        : AssignmentStatus.COMPLETED;
            } else {
                status = AssignmentStatus.IN_PROGRESS;
            }
        } else if (assignment.getDueDate() != null && assignment.getDueDate().isBefore(LocalDateTime.now())) {
            status = AssignmentStatus.OVERDUE;
        } else {
            status = AssignmentStatus.PENDING;
        }

        return new MyAssignmentDto(
                assignment.getId(),
                formId,
                formToTestId.getOrDefault(formId, null),
                assignment.getForm().getTitle(),
                assignment.getForm().getDescription(),
                assignment.isMandatory(),
                assignment.getStartsAt(),
                assignment.getExpiresAt(),
                assignment.getDueDate(),
                assignment.isAllowResubmission(),
                status,
                sessionId,
                startedAt,
                completedAt,
                answeredCount,
                totalQuestions,
                assignment.getForm().getFormType().name()
        );
    }

}
