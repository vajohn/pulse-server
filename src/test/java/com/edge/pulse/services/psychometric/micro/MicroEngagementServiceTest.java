package com.edge.pulse.services.psychometric.micro;

import com.edge.pulse.data.dto.psychometric.CheckInDto;
import com.edge.pulse.data.dto.psychometric.PsychometricQuestionDto;
import com.edge.pulse.data.dto.psychometric.PsychometricSessionDto;
import com.edge.pulse.data.enums.Cadence;
import com.edge.pulse.data.enums.ResultMode;
import com.edge.pulse.data.enums.ScaleProgressState;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.OrganizationalUnit;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.*;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.repositories.FormAssignmentRepository;
import com.edge.pulse.repositories.ResponseSessionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.*;
import com.edge.pulse.services.psychometric.PsychometricSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MicroEngagementServiceTest {

    @Mock AssessmentCadenceRepository cadenceRepository;
    @Mock PsychometricScaleRepository scaleRepository;
    @Mock ScaleProgressRepository scaleProgressRepository;
    @Mock UserItemExposureRepository exposureRepository;
    @Mock ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock FormAssignmentRepository assignmentRepository;
    @Mock UserRepository userRepository;
    @Mock ResponseSessionRepository sessionRepository;
    @Mock PsychometricSessionService sessionService;

    MicroEngagementService service;

    private UUID userId, testId, formId, cadenceId, scaleX, scaleY;
    private User user;
    private PsychometricTest test;
    private AssessmentCadence cadence;

    @BeforeEach
    void setUp() {
        // Real sampler (deterministic) — the point of the design.
        service = new MicroEngagementService(
                cadenceRepository, scaleRepository, scaleProgressRepository, exposureRepository,
                scoringKeyVersionRepository, scoringKeyItemRepository, assignmentRepository,
                userRepository, sessionRepository, sessionService, new ItemSampler());

        userId = UUID.randomUUID();
        testId = UUID.randomUUID();
        formId = UUID.randomUUID();
        cadenceId = UUID.randomUUID();
        scaleX = UUID.randomUUID();
        scaleY = UUID.randomUUID();

        OrganizationalUnit ou = new OrganizationalUnit();
        ou.setId(UUID.randomUUID());
        ou.setPath("EDGE.UNIT.TEAM");
        user = User.builder().id(userId).orgUnit(ou).build();

        Form form = Form.builder().id(formId).title("Micro").build();
        test = PsychometricTest.builder().id(testId).form(form).name("Resilience Pulse")
                .testType(TestType.PERSONALITY).build();

        OrganizationalUnit scopeOu = new OrganizationalUnit();
        scopeOu.setId(UUID.randomUUID());
        scopeOu.setPath("EDGE.UNIT"); // ancestor of user's path
        cadence = AssessmentCadence.builder()
                .id(cadenceId).test(test).cadence(Cadence.WEEKLY).maxItemsPerAdmin(3)
                .orgUnit(scopeOu).includeChildren(true).active(true).build();
    }

    private PsychometricScale scale(UUID id, ResultMode mode) {
        return PsychometricScale.builder().id(id).test(test).name("s-" + id).resultMode(mode).build();
    }

    @Test
    void listCheckIns_returnsActiveInWindowCadencesForUserScope() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(cadenceRepository.findByActiveTrue()).thenReturn(List.of(cadence));
        // 2 CONSOLIDATED scales on the test
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(
                scale(scaleX, ResultMode.CONSOLIDATED),
                scale(scaleY, ResultMode.CONSOLIDATED)));
        // 1 already consolidated for the user
        ScaleProgress consolidated = ScaleProgress.builder()
                .id(UUID.randomUUID()).userId(userId).scaleId(scaleX).testId(testId)
                .windowId(UUID.randomUUID()).itemsRequired(4).itemsCollected(4)
                .state(ScaleProgressState.CONSOLIDATED).build();
        when(scaleProgressRepository.findByUserIdAndTestId(userId, testId))
                .thenReturn(List.of(consolidated));

        List<CheckInDto> out = service.listCheckIns(userId);

        assertThat(out).hasSize(1);
        CheckInDto dto = out.get(0);
        assertThat(dto.scalesConsolidated()).isEqualTo(1);
        assertThat(dto.scalesTotal()).isEqualTo(2);
        assertThat(dto.maxItems()).isEqualTo(3);
        assertThat(dto.testId()).isEqualTo(testId);
        assertThat(dto.formId()).isEqualTo(formId);
    }

    @Test
    void buildCheckInSession_sampledItemsBecomeItemSequence_andExposureRecorded() {
        UUID q1 = UUID.randomUUID(), q2 = UUID.randomUUID(), q3 = UUID.randomUUID(), q4 = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(cadenceRepository.findById(cadenceId)).thenReturn(Optional.of(cadence));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(assignmentRepository.hasVisibleAssignment(formId, userId, "EDGE.UNIT.TEAM")).thenReturn(true);

        ScoringKeyVersion key = ScoringKeyVersion.builder()
                .id(UUID.randomUUID()).test(test).version(1).status(ScoringKeyStatus.ACTIVE).build();
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(testId, ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(key));

        PsychometricScale sx = scale(scaleX, ResultMode.CONSOLIDATED);
        // 4 CONSOLIDATED items, all unseen → sampler draws maxItems=3 of them
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(key.getId())).thenReturn(List.of(
                keyItem(sx, q1), keyItem(sx, q2), keyItem(sx, q3), keyItem(sx, q4)));
        when(scaleRepository.findByTestId(testId)).thenReturn(List.of(sx));
        when(exposureRepository.findByUserIdAndTestId(userId, testId)).thenReturn(List.of());
        when(scaleProgressRepository.findByUserIdAndTestId(userId, testId)).thenReturn(List.of());
        when(exposureRepository.existsById(any())).thenReturn(false);

        // startSession returns the full payload (all 4 questions)
        PsychometricSessionDto fullDto = new PsychometricSessionDto(
                sessionId, "Resilience Pulse", "PERSONALITY", null, null, null, 1000L,
                List.of(q1, q2, q3, q4),
                List.of(qDto(q1), qDto(q2), qDto(q3), qDto(q4)));
        when(sessionService.startSession(formId, userId)).thenReturn(fullDto);

        ResponseSession session = ResponseSession.builder().id(sessionId)
                .form(test.getForm()).user(user).build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PsychometricSessionDto out = service.buildCheckInSession(cadenceId, userId);

        // The returned item sequence is exactly the 3 sampled ids (a subset of the 4 questions)
        assertThat(out.itemSequence()).hasSize(3);
        assertThat(List.of(q1, q2, q3, q4)).containsAll(out.itemSequence());
        assertThat(out.questions()).hasSize(3);
        assertThat(out.questions()).extracting(PsychometricQuestionDto::id)
                .containsExactlyElementsOf(out.itemSequence());

        // The persisted session's itemSequence was narrowed to the sampled ids
        ArgumentCaptor<ResponseSession> sCap = ArgumentCaptor.forClass(ResponseSession.class);
        verify(sessionRepository).save(sCap.capture());
        assertThat(sCap.getValue().getItemSequence()).hasSize(3);

        // Exposure (first_seen) recorded for each sampled id
        ArgumentCaptor<UserItemExposure> eCap = ArgumentCaptor.forClass(UserItemExposure.class);
        verify(exposureRepository, times(3)).save(eCap.capture());
        assertThat(eCap.getAllValues()).allMatch(e -> e.getFirstSeen() != null
                && e.getUserId().equals(userId) && e.getTestId().equals(testId));
    }

    private ScoringKeyItem keyItem(PsychometricScale scale, UUID questionId) {
        Question q = Question.builder().id(questionId).build();
        return ScoringKeyItem.builder().id(UUID.randomUUID()).scoringKey(
                        ScoringKeyVersion.builder().id(UUID.randomUUID()).build())
                .scale(scale).question(q).weight(BigDecimal.ONE).build();
    }

    private PsychometricQuestionDto qDto(UUID id) {
        return new PsychometricQuestionDto(id, "body", "bodyAr", "SCALE", 0,
                1, 5, "lo", "hi", List.of(), false, false, null, null);
    }
}
