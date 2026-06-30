package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.CreateAssignmentRequest;
import com.edge.pulse.data.enums.FormType;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.mappers.AssignmentMapper;
import com.edge.pulse.repositories.*;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.services.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock private FormAssignmentRepository assignmentRepository;
    @Mock private FormRepository formRepository;
    @Mock private OrganizationalUnitRepository orgUnitRepository;
    @Mock private UserRepository userRepository;
    @Mock private ResponseSessionRepository responseSessionRepository;
    @Mock private AnswerSubmissionRepository answerSubmissionRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private FormCacheService cacheService;
    @Mock private AssignmentMapper assignmentMapper;
    @Mock private PsychometricTestRepository psychometricTestRepository;
    @Mock private AuditService auditService;
    private final CacheTtlProperties cacheTtlProps = new CacheTtlProperties();

    private AssignmentService service;

    private static final UUID FORM_ID   = UUID.randomUUID();
    private static final UUID ORG_ID    = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID CALLER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AssignmentService(
                assignmentRepository, formRepository, orgUnitRepository,
                userRepository, responseSessionRepository, answerSubmissionRepository,
                questionRepository, cacheService, assignmentMapper, psychometricTestRepository,
                auditService, cacheTtlProps);
    }

    // -----------------------------------------------------------------------
    // createAssignment — no-questions guard
    // -----------------------------------------------------------------------

    @Test
    void createAssignment_throwsUnprocessable_whenFormHasNoQuestions() {
        Form form = Form.builder().id(FORM_ID).title("Empty form").build();
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formRepository.countActiveQuestionsByFormId(FORM_ID)).thenReturn(0L);

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no content");
    }

    @Test
    void createAssignment_throwsUnprocessable_whenFormHasNoQuestionsUserTarget() {
        Form form = Form.builder().id(FORM_ID).title("Empty form").build();
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formRepository.countActiveQuestionsByFormId(FORM_ID)).thenReturn(0L);

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, null, USER_ID, null, null, null, false, false, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no content");
    }

    @Test
    void createAssignment_proceeds_whenFormHasQuestions() {
        Form form = Form.builder().id(FORM_ID).title("Form with questions").build();
        OrganizationalUnit orgUnit = OrganizationalUnit.builder().id(ORG_ID).path("/org/").build();
        User caller = User.builder().id(CALLER_ID).build();
        FormAssignment saved = FormAssignment.builder().id(UUID.randomUUID()).form(form).build();

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formRepository.countActiveQuestionsByFormId(FORM_ID)).thenReturn(3L);
        when(orgUnitRepository.findById(ORG_ID)).thenReturn(Optional.of(orgUnit));
        when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(caller));
        when(assignmentRepository.existsByFormIdAndOrgUnitIdAndActiveTrue(FORM_ID, ORG_ID))
                .thenReturn(false);
        when(assignmentRepository.save(any())).thenReturn(saved);

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatCode(() -> service.createAssignment(request, CALLER_ID))
                .doesNotThrowAnyException();
    }

    // ----- cache eviction on write (org-targeted writes must invalidate affected users) -----

    @Test
    void createAssignment_orgTarget_evictsAllCachedAssignmentListsByPattern() {
        Form form = Form.builder().id(FORM_ID).title("Form").build();
        OrganizationalUnit orgUnit = OrganizationalUnit.builder().id(ORG_ID).path("/org/").build();
        User caller = User.builder().id(CALLER_ID).build();
        FormAssignment saved = FormAssignment.builder().id(UUID.randomUUID()).form(form).build();

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formRepository.countActiveQuestionsByFormId(FORM_ID)).thenReturn(3L);
        when(orgUnitRepository.findById(ORG_ID)).thenReturn(Optional.of(orgUnit));
        when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(caller));
        when(assignmentRepository.existsByFormIdAndOrgUnitIdAndActiveTrue(FORM_ID, ORG_ID)).thenReturn(false);
        when(assignmentRepository.save(any())).thenReturn(saved);

        service.createAssignment(
                new CreateAssignmentRequest(FORM_ID, ORG_ID, null, null, null, null, true, true, false),
                CALLER_ID);

        verify(cacheService).evictByPattern(FormCacheService.userAssignmentsKeyPattern());
        verify(cacheService, never()).evict(anyString());
    }

    @Test
    void createAssignment_userTarget_evictsOnlyThatUserCache() {
        Form form = Form.builder().id(FORM_ID).title("Form").build();
        User caller = User.builder().id(CALLER_ID).build();
        User target = User.builder().id(USER_ID).build();
        FormAssignment saved = FormAssignment.builder().id(UUID.randomUUID()).form(form).build();

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(formRepository.countActiveQuestionsByFormId(FORM_ID)).thenReturn(3L);
        when(userRepository.findById(CALLER_ID)).thenReturn(Optional.of(caller));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(target));
        when(assignmentRepository.existsByFormIdAndUserIdAndActiveTrue(FORM_ID, USER_ID)).thenReturn(false);
        when(assignmentRepository.save(any())).thenReturn(saved);

        service.createAssignment(
                new CreateAssignmentRequest(FORM_ID, null, USER_ID, null, null, null, true, false, false),
                CALLER_ID);

        verify(cacheService).evict(FormCacheService.userAssignmentsKey(USER_ID));
        verify(cacheService, never()).evictByPattern(anyString());
    }

    @Test
    void deactivateAssignment_orgTarget_evictsAllCachedAssignmentListsByPattern() {
        UUID id = UUID.randomUUID();
        FormAssignment assignment = FormAssignment.builder().id(id).user(null).build();
        when(assignmentRepository.findById(id)).thenReturn(Optional.of(assignment));

        service.deactivateAssignment(id);

        verify(cacheService).evictByPattern(FormCacheService.userAssignmentsKeyPattern());
        verify(cacheService, never()).evict(anyString());
    }

    @Test
    void deactivateAssignment_userTarget_evictsOnlyThatUserCache() {
        UUID id = UUID.randomUUID();
        User target = User.builder().id(USER_ID).build();
        FormAssignment assignment = FormAssignment.builder().id(id).user(target).build();
        when(assignmentRepository.findById(id)).thenReturn(Optional.of(assignment));

        service.deactivateAssignment(id);

        verify(cacheService).evict(FormCacheService.userAssignmentsKey(USER_ID));
        verify(cacheService, never()).evictByPattern(anyString());
    }

    @Test
    void createAssignment_throwsIllegalArgument_whenFormNotFound() {
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.empty());

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Form not found");
    }

    @Test
    void createAssignment_throwsIllegalArgument_whenBothTargetsProvided() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, USER_ID, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both");
    }


    @Test
    void createAssignment_throwsUnprocessable_whenPsychometricFormHasNoScales() {
        Form form = Form.builder().id(FORM_ID).formType(com.edge.pulse.data.enums.FormType.PSYCHOMETRIC).title("Empty psychometric").build();
        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(psychometricTestRepository.countScalesByFormId(FORM_ID)).thenReturn(0L);

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no content");
    }

    @Test
    void createAssignment_throwsIllegalArgument_whenNoTargetProvided() {
        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, null, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must target");
    }

    // -----------------------------------------------------------------------
    // Task 12 — ACTIVE gate for psychometric form assignments
    // -----------------------------------------------------------------------

    @Test
    void createAssignment_throwsConflict_whenPsychometricFormTargetsDraftTest() {
        Form form = Form.builder().id(FORM_ID).formType(FormType.PSYCHOMETRIC).title("Draft psych form").build();
        PsychometricTest draftTest = PsychometricTest.builder()
                .id(UUID.randomUUID()).status(TestStatus.DRAFT).build();

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(psychometricTestRepository.countScalesByFormId(FORM_ID)).thenReturn(3L);
        when(psychometricTestRepository.findByFormId(FORM_ID)).thenReturn(Optional.of(draftTest));

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            org.springframework.http.HttpStatus.CONFLICT, rse.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertTrue(
                            rse.getReason() != null &&
                            rse.getReason().toLowerCase().contains("active"),
                            "Error message must mention ACTIVE");
                });
    }

    @Test
    void createAssignment_throwsConflict_whenPsychometricFormTargetsPendingApprovalTest() {
        Form form = Form.builder().id(FORM_ID).formType(FormType.PSYCHOMETRIC).title("Pending psych form").build();
        PsychometricTest pendingTest = PsychometricTest.builder()
                .id(UUID.randomUUID()).status(TestStatus.PENDING_APPROVAL).build();

        when(formRepository.findById(FORM_ID)).thenReturn(Optional.of(form));
        when(psychometricTestRepository.countScalesByFormId(FORM_ID)).thenReturn(3L);
        when(psychometricTestRepository.findByFormId(FORM_ID)).thenReturn(Optional.of(pendingTest));

        CreateAssignmentRequest request = new CreateAssignmentRequest(
                FORM_ID, ORG_ID, null, null, null, null, true, true, false);

        assertThatThrownBy(() -> service.createAssignment(request, CALLER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    org.junit.jupiter.api.Assertions.assertEquals(
                            org.springframework.http.HttpStatus.CONFLICT, rse.getStatusCode());
                });
    }
}
