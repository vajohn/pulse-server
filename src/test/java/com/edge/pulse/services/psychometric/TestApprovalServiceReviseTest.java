package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.enums.TestStatus;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.Form;
import com.edge.pulse.data.models.Question;
import com.edge.pulse.data.models.User;
import com.edge.pulse.data.models.psychometric.CompetencyScaleWeight;
import com.edge.pulse.data.models.psychometric.NormEntry;
import com.edge.pulse.data.models.psychometric.NormTableVersion;
import com.edge.pulse.data.models.psychometric.PsychometricScale;
import com.edge.pulse.data.models.psychometric.PsychometricTest;
import com.edge.pulse.data.models.psychometric.ResultVisibilityPolicy;
import com.edge.pulse.data.models.psychometric.ScoringKeyItem;
import com.edge.pulse.data.models.psychometric.ScoringKeyVersion;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ResultAudience;
import com.edge.pulse.repositories.CandidateAnswerRepository;
import com.edge.pulse.repositories.FormRepository;
import com.edge.pulse.repositories.QuestionRepository;
import com.edge.pulse.repositories.UserRepository;
import com.edge.pulse.repositories.psychometric.CompetencyScaleWeightRepository;
import com.edge.pulse.repositories.psychometric.NormEntryRepository;
import com.edge.pulse.repositories.psychometric.NormTableVersionRepository;
import com.edge.pulse.repositories.psychometric.PsychometricScaleRepository;
import com.edge.pulse.repositories.psychometric.PsychometricTestRepository;
import com.edge.pulse.repositories.psychometric.ResultVisibilityPolicyRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyCorrectAnswerRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyItemRepository;
import com.edge.pulse.repositories.psychometric.ScoringKeyVersionRepository;
import com.edge.pulse.repositories.psychometric.TestApprovalRequestRepository;
import com.edge.pulse.services.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TestApprovalServiceReviseTest {

    @Mock private PsychometricTestRepository testRepository;
    @Mock private UserRepository userRepository;
    @Mock private FormRepository formRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private CandidateAnswerRepository candidateAnswerRepository;
    @Mock private TestApprovalRequestRepository approvalRequestRepository;
    @Mock private ScoringKeyVersionRepository scoringKeyVersionRepository;
    @Mock private ScoringKeyItemRepository scoringKeyItemRepository;
    @Mock private ScoringKeyCorrectAnswerRepository scoringKeyCorrectAnswerRepository;
    @Mock private NormTableVersionRepository normTableVersionRepository;
    @Mock private NormEntryRepository normEntryRepository;
    @Mock private PsychometricScaleRepository scaleRepository;
    @Mock private CompetencyScaleWeightRepository competencyScaleWeightRepository;
    @Mock private ResultVisibilityPolicyRepository resultVisibilityPolicyRepository;
    @Mock private com.edge.pulse.mappers.psychometric.TestApprovalMapper approvalMapper;
    @Mock private AuditService auditService;
    @Mock private PsychometricAdminService psychometricAdminService;

    @InjectMocks
    private TestApprovalService approvalService;

    private PsychometricTest buildActiveTest() {
        Form form = Form.builder().id(UUID.randomUUID()).build();
        return PsychometricTest.builder()
                .id(UUID.randomUUID())
                .name("Test One")
                .description("description")
                .instructions("instructions")
                .testType(TestType.PERSONALITY)
                .status(TestStatus.ACTIVE)
                .version(1)
                .form(form)
                .build();
    }

    /** Stubs the empty-collections baseline so the deep-copy paths are exercised but return nothing. */
    private void stubEmptyDeepCopy(PsychometricTest prior) {
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestIdOrdered(prior.getId())).thenReturn(List.of());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());
        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId())).thenReturn(List.of());
    }

    @Test
    void reviseClonesActiveIntoNewDraftVersion() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");
        stubEmptyDeepCopy(prior);

        PsychometricTest clone = approvalService.revise(prior.getId(), userId);

        assertThat(clone.getStatus()).isEqualTo(TestStatus.DRAFT);
        assertThat(clone.getVersion()).isEqualTo(prior.getVersion() + 1);
        assertThat(clone.getSupersedes()).isSameAs(prior);
        assertThat(clone.getName()).isEqualTo(prior.getName());
        assertThat(clone.getTestType()).isEqualTo(prior.getTestType());
        assertThat(clone.getCreatedBy().getId()).isEqualTo(userId);

        // Prior remains ACTIVE (not changed until new version is approved)
        assertThat(prior.getStatus()).isEqualTo(TestStatus.ACTIVE);

        ArgumentCaptor<PsychometricTest> captor = ArgumentCaptor.forClass(PsychometricTest.class);
        verify(testRepository).save(captor.capture());
        PsychometricTest saved = captor.getValue();
        assertThat(saved.getVersion()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(TestStatus.DRAFT);
        assertThat(saved.getSupersedes()).isSameAs(prior);

        verify(auditService).logAction(eq(userId), eq("PSYCH_TEST_REVISED"),
                eq("PsychometricTest"), eq(prior.getId()), any(), isNull());
    }

    @ParameterizedTest
    @EnumSource(value = TestStatus.class, names = {"DRAFT", "PENDING_APPROVAL", "RETIRED"})
    void reviseRejectsNonActive409(TestStatus nonActiveStatus) {
        UUID userId = UUID.randomUUID();
        PsychometricTest test = buildActiveTest();
        test.setStatus(nonActiveStatus);

        when(testRepository.findById(test.getId())).thenReturn(Optional.of(test));

        assertThatThrownBy(() -> approvalService.revise(test.getId(), userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── C1: deep-copy assertions ─────────────────────────────────────────────

    @Test
    void reviseDeepCopiesQuestionsOntoNewForm() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        Question priorQ = Question.builder()
                .id(UUID.randomUUID())
                .form(prior.getForm())
                .body("What is 2+2?")
                .questionType(QuestionType.CHOICE_SINGLE)
                .displayOrder(1)
                .scaleMin(null)
                .scaleMax(null)
                .build();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> {
            Form f = inv.getArgument(0);
            f = Form.builder().id(UUID.randomUUID()).title(f.getTitle())
                    .description(f.getDescription()).formType(f.getFormType())
                    .anonWindowMinutes(0).build();
            return f;
        });
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        // Deep-copy stubs
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of(priorQ));
        when(questionRepository.save(any())).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            // Assign an id to simulate DB persistence
            return Question.builder().id(UUID.randomUUID()).form(q.getForm())
                    .body(q.getBody()).questionType(q.getQuestionType())
                    .displayOrder(q.getDisplayOrder()).build();
        });
        when(candidateAnswerRepository.findByQuestionIdOrderByDisplayOrderAsc(priorQ.getId()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestIdOrdered(prior.getId())).thenReturn(List.of());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());
        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId())).thenReturn(List.of());

        approvalService.revise(prior.getId(), userId);

        // Verify a new question was saved with same body/type onto a different form
        ArgumentCaptor<Question> qCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionRepository).save(qCaptor.capture());
        Question savedQ = qCaptor.getValue();
        assertThat(savedQ.getBody()).isEqualTo("What is 2+2?");
        assertThat(savedQ.getQuestionType()).isEqualTo(QuestionType.CHOICE_SINGLE);
        assertThat(savedQ.getDisplayOrder()).isEqualTo(1);
        // The new question belongs to the NEW form, not prior.form
        assertThat(savedQ.getForm().getId()).isNotEqualTo(prior.getForm().getId());
    }

    @Test
    void reviseDeepCopiesScalesWithParentRemap() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        PsychometricScale rootScale = PsychometricScale.builder()
                .id(UUID.randomUUID())
                .test(prior)
                .name("Root")
                .displayOrder(0)
                .build();
        PsychometricScale childScale = PsychometricScale.builder()
                .id(UUID.randomUUID())
                .test(prior)
                .name("Child")
                .parentScale(rootScale)
                .displayOrder(1)
                .build();

        UUID newRootScaleId = UUID.randomUUID();
        UUID newChildScaleId = UUID.randomUUID();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> Form.builder().id(UUID.randomUUID())
                .title("T").formType(prior.getForm().getFormType()).anonWindowMinutes(0).build());
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestIdOrdered(prior.getId()))
                .thenReturn(List.of(rootScale, childScale));
        // Simulate saving root scale → returns new scale with newRootScaleId
        when(scaleRepository.save(any())).thenAnswer(inv -> {
            PsychometricScale s = inv.getArgument(0);
            UUID newId = s.getParentScale() == null ? newRootScaleId : newChildScaleId;
            return PsychometricScale.builder()
                    .id(newId)
                    .test(s.getTest())
                    .parentScale(s.getParentScale())
                    .name(s.getName())
                    .displayOrder(s.getDisplayOrder())
                    .build();
        });
        // When child pass looks up the new root by id:
        when(scaleRepository.findById(newRootScaleId)).thenReturn(Optional.of(
                PsychometricScale.builder().id(newRootScaleId).test(prior).name("Root").build()));

        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());
        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId())).thenReturn(List.of());

        approvalService.revise(prior.getId(), userId);

        // Two scales saved: root then child
        ArgumentCaptor<PsychometricScale> sCaptor = ArgumentCaptor.forClass(PsychometricScale.class);
        verify(scaleRepository, times(2)).save(sCaptor.capture());
        List<PsychometricScale> saved = sCaptor.getAllValues();

        PsychometricScale savedRoot = saved.get(0);
        PsychometricScale savedChild = saved.get(1);

        assertThat(savedRoot.getParentScale()).isNull();
        assertThat(savedRoot.getName()).isEqualTo("Root");

        assertThat(savedChild.getName()).isEqualTo("Child");
        // The child's parent must be the NEW root (looked up by newRootScaleId), not the old root
        assertThat(savedChild.getParentScale()).isNotNull();
        assertThat(savedChild.getParentScale().getId()).isEqualTo(newRootScaleId);
    }

    @Test
    void reviseDeepCopiesScoringKeyItemsRemappingQuestionAndScale() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        // Prior question
        Question priorQ = Question.builder().id(UUID.randomUUID()).form(prior.getForm())
                .body("Q1").questionType(QuestionType.CHOICE_SINGLE).displayOrder(1).build();
        // Prior scale
        PsychometricScale priorScale = PsychometricScale.builder()
                .id(UUID.randomUUID()).test(prior).name("S1").displayOrder(0).build();
        // Prior scoring key
        ScoringKeyVersion priorKey = ScoringKeyVersion.builder()
                .id(UUID.randomUUID()).test(prior).version(1).status(ScoringKeyStatus.ACTIVE).build();
        // Prior scoring key item
        ScoringKeyItem priorItem = ScoringKeyItem.builder()
                .id(UUID.randomUUID()).scoringKey(priorKey).question(priorQ)
                .scale(priorScale).direction(ScoreDirection.FORWARD)
                .weight(BigDecimal.ONE).build();

        UUID newQuestionId = UUID.randomUUID();
        UUID newScaleId = UUID.randomUUID();
        UUID newKeyId = UUID.randomUUID();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> Form.builder().id(UUID.randomUUID())
                .title("T").formType(prior.getForm().getFormType()).anonWindowMinutes(0).build());
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        // Questions: one prior question → one new question with newQuestionId
        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of(priorQ));
        when(questionRepository.save(any())).thenAnswer(inv ->
                Question.builder().id(newQuestionId).form(((Question) inv.getArgument(0)).getForm())
                        .body(((Question) inv.getArgument(0)).getBody())
                        .questionType(((Question) inv.getArgument(0)).getQuestionType())
                        .displayOrder(((Question) inv.getArgument(0)).getDisplayOrder()).build());
        when(candidateAnswerRepository.findByQuestionIdOrderByDisplayOrderAsc(priorQ.getId()))
                .thenReturn(List.of());

        // Scales: one root scale → new scale with newScaleId
        when(scaleRepository.findByTestIdOrdered(prior.getId())).thenReturn(List.of(priorScale));
        when(scaleRepository.save(any())).thenAnswer(inv ->
                PsychometricScale.builder().id(newScaleId).test(((PsychometricScale) inv.getArgument(0)).getTest())
                        .name(((PsychometricScale) inv.getArgument(0)).getName()).displayOrder(0).build());

        // Scoring key
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.of(priorKey));
        when(scoringKeyVersionRepository.save(any())).thenAnswer(inv ->
                ScoringKeyVersion.builder().id(newKeyId).test(((ScoringKeyVersion) inv.getArgument(0)).getTest())
                        .version(1).status(ScoringKeyStatus.ACTIVE).build());
        when(scoringKeyItemRepository.findByScoringKeyIdWithDetails(priorKey.getId()))
                .thenReturn(List.of(priorItem));
        // questionRepository.findById for newQuestionId
        when(questionRepository.findById(newQuestionId)).thenReturn(Optional.of(
                Question.builder().id(newQuestionId).form(prior.getForm()).body("Q1")
                        .questionType(QuestionType.CHOICE_SINGLE).displayOrder(1).build()));
        // scaleRepository.findById for newScaleId
        when(scaleRepository.findById(newScaleId)).thenReturn(Optional.of(
                PsychometricScale.builder().id(newScaleId).test(prior).name("S1").displayOrder(0).build()));
        when(scoringKeyItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // No CHOICE_MULTIPLE correct answers (priorItem.correctAnswer == null, so these stubs not needed)
        when(scoringKeyCorrectAnswerRepository.findByItemIdIn(any())).thenReturn(List.of());

        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());
        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId())).thenReturn(List.of());

        approvalService.revise(prior.getId(), userId);

        // Verify scoring key item saved with remapped question and scale
        ArgumentCaptor<ScoringKeyItem> itemCaptor = ArgumentCaptor.forClass(ScoringKeyItem.class);
        verify(scoringKeyItemRepository).save(itemCaptor.capture());
        ScoringKeyItem savedItem = itemCaptor.getValue();

        assertThat(savedItem.getQuestion().getId()).isEqualTo(newQuestionId);
        assertThat(savedItem.getScale().getId()).isEqualTo(newScaleId);
        assertThat(savedItem.getDirection()).isEqualTo(ScoreDirection.FORWARD);
        assertThat(savedItem.getWeight()).isEqualTo(BigDecimal.ONE);
    }

    @Test
    void reviseDeepCopiesNormEntriesRemappingScale() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        PsychometricScale priorScale = PsychometricScale.builder()
                .id(UUID.randomUUID()).test(prior).name("S1").displayOrder(0).build();
        NormTableVersion priorNorm = NormTableVersion.builder()
                .id(UUID.randomUUID()).test(prior).version(1)
                .label("Norms 2026").status(NormStatus.VALIDATED).build();
        NormEntry priorEntry = NormEntry.builder()
                .id(UUID.randomUUID()).normTable(priorNorm).scale(priorScale)
                .rawScoreMin(BigDecimal.ZERO).rawScoreMax(new BigDecimal("10"))
                .stenScore(new BigDecimal("5")).build();

        UUID newScaleId = UUID.randomUUID();
        UUID newNormId = UUID.randomUUID();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> Form.builder().id(UUID.randomUUID())
                .title("T").formType(prior.getForm().getFormType()).anonWindowMinutes(0).build());
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestIdOrdered(prior.getId())).thenReturn(List.of(priorScale));
        when(scaleRepository.save(any())).thenAnswer(inv ->
                PsychometricScale.builder().id(newScaleId)
                        .test(((PsychometricScale) inv.getArgument(0)).getTest())
                        .name(((PsychometricScale) inv.getArgument(0)).getName()).displayOrder(0).build());

        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.of(priorNorm));
        when(normTableVersionRepository.save(any())).thenAnswer(inv ->
                NormTableVersion.builder().id(newNormId)
                        .test(((NormTableVersion) inv.getArgument(0)).getTest())
                        .version(1).label("Norms 2026").status(NormStatus.VALIDATED).build());
        when(normEntryRepository.findByNormTableId(priorNorm.getId()))
                .thenReturn(List.of(priorEntry));
        when(scaleRepository.findById(newScaleId)).thenReturn(Optional.of(
                PsychometricScale.builder().id(newScaleId).test(prior).name("S1").displayOrder(0).build()));
        when(normEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId())).thenReturn(List.of());

        approvalService.revise(prior.getId(), userId);

        // Verify norm entry saved with remapped scale
        ArgumentCaptor<NormEntry> neCaptor = ArgumentCaptor.forClass(NormEntry.class);
        verify(normEntryRepository).save(neCaptor.capture());
        NormEntry savedEntry = neCaptor.getValue();

        assertThat(savedEntry.getScale().getId()).isEqualTo(newScaleId);
        assertThat(savedEntry.getStenScore()).isEqualTo(new BigDecimal("5"));
        assertThat(savedEntry.getRawScoreMin()).isEqualTo(BigDecimal.ZERO);
        assertThat(savedEntry.getNormTable().getId()).isEqualTo(newNormId);
    }

    @Test
    void reviseDeepCopiesVisibilityPolicies() {
        UUID userId = UUID.randomUUID();
        PsychometricTest prior = buildActiveTest();
        User user = User.builder().id(userId).build();

        ResultVisibilityPolicy priorPolicy = ResultVisibilityPolicy.builder()
                .id(UUID.randomUUID()).test(prior)
                .audience(ResultAudience.CANDIDATE)
                .showRawScore(true).showStenProfile(true)
                .showPercentile(false).showCompetencyMap(false)
                .showPassFailOnly(false).showScaleBreakdown(true)
                .build();

        when(testRepository.findById(prior.getId())).thenReturn(Optional.of(prior));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(formRepository.save(any())).thenAnswer(inv -> Form.builder().id(UUID.randomUUID())
                .title("T").formType(prior.getForm().getFormType()).anonWindowMinutes(0).build());
        when(testRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(auditService.buildDetail(any(), any(), any(), any(), any(), any())).thenReturn("{}");

        when(questionRepository.findByFormIdOrderByDisplayOrderAsc(prior.getForm().getId()))
                .thenReturn(List.of());
        when(scaleRepository.findByTestIdOrdered(prior.getId())).thenReturn(List.of());
        when(scoringKeyVersionRepository.findFirstByTestIdAndStatus(prior.getId(), ScoringKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(normTableVersionRepository.findFirstByTestIdAndStatus(prior.getId(), NormStatus.VALIDATED))
                .thenReturn(Optional.empty());
        when(scaleRepository.findByTestId(prior.getId())).thenReturn(List.of());
        when(resultVisibilityPolicyRepository.findByTestId(prior.getId()))
                .thenReturn(List.of(priorPolicy));
        when(resultVisibilityPolicyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        approvalService.revise(prior.getId(), userId);

        ArgumentCaptor<ResultVisibilityPolicy> polCaptor =
                ArgumentCaptor.forClass(ResultVisibilityPolicy.class);
        verify(resultVisibilityPolicyRepository).save(polCaptor.capture());
        ResultVisibilityPolicy savedPol = polCaptor.getValue();

        assertThat(savedPol.getAudience()).isEqualTo(ResultAudience.CANDIDATE);
        assertThat(savedPol.isShowRawScore()).isTrue();
        assertThat(savedPol.isShowStenProfile()).isTrue();
        assertThat(savedPol.isShowPercentile()).isFalse();
        assertThat(savedPol.isShowPassFailOnly()).isFalse();
        assertThat(savedPol.isShowScaleBreakdown()).isTrue();
    }
}
