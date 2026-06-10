package com.edge.pulse.services;

import com.edge.pulse.configs.CacheTtlProperties;
import com.edge.pulse.data.dto.CreateAssignmentRequest;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.FormAssignment;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.User;
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
}
