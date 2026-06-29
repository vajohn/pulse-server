package com.edge.pulse.services.psychometric.imports;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.dto.psychometric.PsychometricScaleDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.ScoringKeyItemRequest;
import com.edge.pulse.data.dto.psychometric.imports.AnswerKeyEntry;
import com.edge.pulse.data.dto.psychometric.imports.ImportPackageRequest;
import com.edge.pulse.data.dto.psychometric.imports.ImportResultDto;
import com.edge.pulse.data.dto.psychometric.imports.NormScaleParamRequest;
import com.edge.pulse.data.dto.psychometric.imports.ParsedOption;
import com.edge.pulse.data.dto.psychometric.imports.ParsedPackage;
import com.edge.pulse.data.dto.psychometric.imports.ParsedQuestion;
import com.edge.pulse.data.dto.psychometric.imports.ScoringSheetItem;
import com.edge.pulse.data.dto.psychometric.imports.ScoringSheetScale;
import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.enums.ScoreMethod;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.data.models.psychometric.PsychometricAsset;
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import com.edge.pulse.services.psychometric.assets.AssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentImporterTest {

    @Mock PsychometricAdminService admin;
    @Mock AssetService assetService;
    @InjectMocks AssessmentImporter importer;

    // ── DTO factories (match the real record field order) ──────────────────────

    private static PsychometricTestDto testDto(UUID testId) {
        return new PsychometricTestDto(testId, UUID.randomUUID(), "ATP", "desc", null,
                "PERSONALITY", null, "DRAFT", 1, LocalDateTime.now(), 0, 0, null, null, null, null);
    }

    private static QuestionDto questionDto(UUID qId, UUID optId1, UUID optId2) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.CHOICE_SINGLE, 0,
                null, null,
                List.of(CandidateAnswerDto.of(optId1, "A", "أ", 0, null, null),
                        CandidateAnswerDto.of(optId2, "B", "ب", 1, null, null)),
                null, null, false, null, null, null, null, null, null, null);
    }

    private static QuestionDto questionDtoSingleOpt(UUID qId, UUID optId) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.SCALE, 0,
                null, null,
                List.of(CandidateAnswerDto.of(optId, "A", "أ", 0, null, null)),
                null, null, false, null, null, null, null, null, null, null);
    }

    /** A SCALE question carries no candidate answers (answered via answer_scale.value). */
    private static QuestionDto scaleQuestionDto(UUID qId) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.SCALE, 0,
                null, null,
                List.of(),
                null, null, false, null, null, null, null, null, null, null);
    }

    private static PsychometricScaleDto scaleDto(UUID scaleId, String name) {
        return new PsychometricScaleDto(scaleId, UUID.randomUUID(), null, name, null,
                "SUM", 0, null, null, null);
    }

    @Test
    void importsValidPackage_wiresNamesToIds() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();
        UUID optId1 = UUID.randomUUID();
        UUID optId2 = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(questionDto(qId, optId1, optId2));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "Agility"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(new ScoringSheetScale("Agility", null, ScoreMethod.SUM, NormStrategyType.PARAMETRIC,
                        new BigDecimal("7.92"), new BigDecimal("3.10"),
                        new BigDecimal("10"), new BigDecimal("50"),
                        new BigDecimal("10"), new BigDecimal("120"),
                        null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "Agility", ScoreDirection.FORWARD,
                        ItemStrategyType.BINARY_FORCED_CHOICE, 1.0, null)),
                List.of());

        ImportResultDto res = importer.importPackage(
                new ImportPackageRequest("ATP", "desc", TestType.PERSONALITY, null, null), pkg, UUID.randomUUID());

        assertThat(res.success()).isTrue();
        assertThat(res.testId()).isEqualTo(testId);
        assertThat(res.questions()).isEqualTo(1);
        assertThat(res.scales()).isEqualTo(1);
        assertThat(res.items()).isEqualTo(1);
        assertThat(res.normParams()).isEqualTo(1);
        assertThat(res.errors()).isEmpty();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoringKeyItemRequest>> keyCap = ArgumentCaptor.forClass(List.class);
        verify(admin).saveScoringKey(eq(testId), keyCap.capture(), any());
        ScoringKeyItemRequest item = keyCap.getValue().get(0);
        assertThat(item.scaleId()).isEqualTo(scaleId);
        assertThat(item.questionId()).isEqualTo(qId);
        assertThat(item.itemStrategy()).isEqualTo(ItemStrategyType.BINARY_FORCED_CHOICE);
        assertThat(item.direction()).isEqualTo("FORWARD");

        verify(admin).saveParametricNorms(eq(testId), anyList(), any());
    }

    @Test
    void answerKeySingle_resolvesCorrectAnswerIdFromKeyedValue() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();
        UUID optId1 = UUID.randomUUID();  // value 1
        UUID optId2 = UUID.randomUUID();  // value 2

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(questionDto(qId, optId1, optId2));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "IQ"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(new ScoringSheetScale("IQ", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "IQ", ScoreDirection.FORWARD,
                        ItemStrategyType.ANSWER_KEY_SINGLE, 1.0, null)),
                List.of(new AnswerKeyEntry("Q1", 2)));

        importer.importPackage(
                new ImportPackageRequest("CA", "desc", TestType.COGNITIVE, 600, null), pkg, UUID.randomUUID());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoringKeyItemRequest>> keyCap = ArgumentCaptor.forClass(List.class);
        verify(admin).saveScoringKey(eq(testId), keyCap.capture(), any());
        assertThat(keyCap.getValue().get(0).correctAnswerId()).isEqualTo(optId2);
        // No PARAMETRIC scale → no parametric norms call.
        verify(admin, never()).saveParametricNorms(any(), anyList(), any());
    }

    @Test
    void conflictingStrategiesForSameQuestion_throwsIllegalArgument() {
        UUID testId = UUID.randomUUID();

        // questionTypeFor throws before addQuestion or createScale are called, so only createTest
        // needs to be stubbed.
        when(admin.createTest(any(), any())).thenReturn(testDto(testId));

        // Q1 referenced by LIKERT_VALUE (→SCALE) and ANSWER_KEY_SINGLE (→CHOICE_SINGLE) — conflict
        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(
                        new ScoringSheetScale("S1", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                                null, null, null, null, null, null, null, null, List.of(), null, false),
                        new ScoringSheetScale("S2", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                                null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(
                        new ScoringSheetItem("Q1", "S1", ScoreDirection.FORWARD,
                                ItemStrategyType.LIKERT_VALUE, 1.0, null),
                        new ScoringSheetItem("Q1", "S2", ScoreDirection.FORWARD,
                                ItemStrategyType.ANSWER_KEY_SINGLE, 1.0, null)),
                List.of());

        assertThatThrownBy(() -> importer.importPackage(
                new ImportPackageRequest("X", null, TestType.PERSONALITY, null, null), pkg, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conflicting question types");
    }

    @Test
    void sameStrategyMultipleItems_allowedNoConflict() {
        // VIP-style: same question referenced by multiple OPTION_TAGGED_TALLY items (different scales)
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId1 = UUID.randomUUID();
        UUID scaleId2 = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any()))
                .thenReturn(questionDto(qId, UUID.randomUUID(), UUID.randomUUID()));
        when(admin.createScale(eq(testId), any(), any()))
                .thenReturn(scaleDto(scaleId1, "S1"))
                .thenReturn(scaleDto(scaleId2, "S2"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(
                        new ScoringSheetScale("S1", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                                null, null, null, null, null, null, null, null, List.of(), null, false),
                        new ScoringSheetScale("S2", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                                null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(
                        new ScoringSheetItem("Q1", "S1", ScoreDirection.FORWARD,
                                ItemStrategyType.OPTION_TAGGED_TALLY, 1.0, null),
                        new ScoringSheetItem("Q1", "S2", ScoreDirection.FORWARD,
                                ItemStrategyType.OPTION_TAGGED_TALLY, 1.0, null)),
                List.of());

        // Should not throw
        ImportResultDto res = importer.importPackage(
                new ImportPackageRequest("VIP", null, TestType.PERSONALITY, null, null), pkg, UUID.randomUUID());
        assertThat(res.success()).isTrue();
        assertThat(res.items()).isEqualTo(2);
    }

    @Test
    void likertValueItem_buildsScaleQuestionWithRangeAndNoOptions() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(scaleQuestionDto(qId));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "Extraversion"));

        // 5-point Likert: values 1..5, lowest label "Strongly Disagree", highest "Strongly Agree".
        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("Strongly Disagree", "لا أوافق بشدة", 1, 0),
                                new ParsedOption("Disagree", "لا أوافق", 2, 1),
                                new ParsedOption("Neutral", "محايد", 3, 2),
                                new ParsedOption("Agree", "أوافق", 4, 3),
                                new ParsedOption("Strongly Agree", "أوافق بشدة", 5, 4)))),
                List.of(new ScoringSheetScale("Extraversion", null, ScoreMethod.SUM,
                        NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "Extraversion", ScoreDirection.FORWARD,
                        ItemStrategyType.LIKERT_VALUE, 1.0, null)),
                List.of());

        ImportResultDto res = importer.importPackage(
                new ImportPackageRequest("BFI", "desc", TestType.PERSONALITY, null, null), pkg, UUID.randomUUID());
        assertThat(res.success()).isTrue();

        ArgumentCaptor<AddQuestionRequest> qCap = ArgumentCaptor.forClass(AddQuestionRequest.class);
        verify(admin).addQuestion(eq(testId), qCap.capture(), any());
        AddQuestionRequest built = qCap.getValue();
        assertThat(built.questionType()).isEqualTo(QuestionType.SCALE);
        assertThat(built.scaleMin()).isEqualTo(1);
        assertThat(built.scaleMax()).isEqualTo(5);
        assertThat(built.minLabel()).isEqualTo("Strongly Disagree");
        assertThat(built.maxLabel()).isEqualTo("Strongly Agree");
        assertThat(built.minLabelAr()).isEqualTo("لا أوافق بشدة");
        assertThat(built.maxLabelAr()).isEqualTo("أوافق بشدة");
        // SCALE questions carry NO candidate answers (DB chk_scale_range + answer_scale.value path).
        assertThat(built.candidateAnswers()).isNullOrEmpty();
    }

    @Test
    void unmappedLikertQuestion_inferredAsScale_allowedForPersonality() {
        // PTI-style: a consistency/validity item with NO scoring item row. Must NOT default to
        // CHOICE_SINGLE (rejected for PERSONALITY); inferred SCALE from contiguous 1..N options.
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(scaleQuestionDto(qId));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "Dominance"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(
                        // Mapped scored question
                        new ParsedQuestion("Q1", "Stmt", "بيان",
                                List.of(new ParsedOption("Low", "منخفض", 1, 0),
                                        new ParsedOption("Mid", "متوسط", 2, 1),
                                        new ParsedOption("High", "مرتفع", 3, 2))),
                        // UNMAPPED validity item — 4-point contiguous scale, no scoring row
                        new ParsedQuestion("VAL1", "Consistency", "اتساق",
                                List.of(new ParsedOption("Never", "أبداً", 1, 0),
                                        new ParsedOption("Sometimes", "أحياناً", 2, 1),
                                        new ParsedOption("Often", "غالباً", 3, 2),
                                        new ParsedOption("Always", "دائماً", 4, 3)))),
                List.of(new ScoringSheetScale("Dominance", null, ScoreMethod.SUM,
                        NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "Dominance", ScoreDirection.FORWARD,
                        ItemStrategyType.LIKERT_VALUE, 1.0, null)),
                List.of());

        ImportResultDto res = importer.importPackage(
                new ImportPackageRequest("PTI", "desc", TestType.PERSONALITY, null, null), pkg, UUID.randomUUID());
        assertThat(res.success()).isTrue();
        assertThat(res.questions()).isEqualTo(2);

        // Both questions built as SCALE — neither CHOICE_SINGLE (which PERSONALITY forbids).
        ArgumentCaptor<AddQuestionRequest> qCap = ArgumentCaptor.forClass(AddQuestionRequest.class);
        verify(admin, org.mockito.Mockito.times(2)).addQuestion(eq(testId), qCap.capture(), any());
        assertThat(qCap.getAllValues())
                .allSatisfy(r -> assertThat(r.questionType()).isEqualTo(QuestionType.SCALE));
    }

    @Test
    void unknownScaleReference_throwsAndRollsBack() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        // Single-option mock matches the single parsed option (displayOrder=0)
        when(admin.addQuestion(eq(testId), any(), any()))
                .thenReturn(questionDtoSingleOpt(qId, UUID.randomUUID()));
        when(admin.createScale(eq(testId), any(), any()))
                .thenReturn(scaleDto(UUID.randomUUID(), "Agility"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0)))),
                List.of(new ScoringSheetScale("Agility", null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "Nonexistent", ScoreDirection.FORWARD,
                        ItemStrategyType.LIKERT_VALUE, 1.0, null)),
                List.of());

        assertThatThrownBy(() -> importer.importPackage(
                new ImportPackageRequest("X", null, TestType.PERSONALITY, null, null), pkg, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown scale");

        verify(admin, never()).saveScoringKey(any(), anyList(), any());
    }

    @Test
    void importPackage_withImages_rehostsAndRewritesBody() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();
        UUID optId1 = UUID.randomUUID();
        UUID optId2 = UUID.randomUUID();
        UUID assetId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(questionDto(qId, optId1, optId2));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "ATD"));
        when(assetService.store(any(), any(), eq("Q1.png"), any()))
                .thenReturn(PsychometricAsset.builder().id(assetId).build());

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1",
                        "How many differences?\n![Q1.png](/api/backend/files/abc.png)", "بيان",
                        List.of(new ParsedOption("A", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(new ScoringSheetScale("ATD", null, ScoreMethod.SUM,
                        NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "ATD", ScoreDirection.FORWARD,
                        ItemStrategyType.ANSWER_KEY_SINGLE, 1.0, null)),
                List.of(new AnswerKeyEntry("Q1", 2)));

        Map<String, byte[]> images = Map.of("Q1.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        importer.importPackage(
                new ImportPackageRequest("ATD", "desc", TestType.COGNITIVE, 600, null), pkg, images, UUID.randomUUID());

        ArgumentCaptor<AddQuestionRequest> qCap = ArgumentCaptor.forClass(AddQuestionRequest.class);
        verify(admin).addQuestion(eq(testId), qCap.capture(), any());
        AddQuestionRequest built = qCap.getValue();
        assertThat(built.body()).contains("/api/psychometric/assets/" + assetId);
        assertThat(built.body()).doesNotContain("/api/backend/files");
    }

    @Test
    void importPackage_withImages_setsOptionImage_andStripsLabel() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID scaleId = UUID.randomUUID();
        UUID optId1 = UUID.randomUUID();
        UUID optId2 = UUID.randomUUID();
        UUID assetId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(questionDto(qId, optId1, optId2));
        when(admin.createScale(eq(testId), any(), any())).thenReturn(scaleDto(scaleId, "ATD"));
        when(assetService.store(any(), any(), eq("optA.png"), any()))
                .thenReturn(PsychometricAsset.builder().id(assetId).build());

        // Option A's EN label carries a markdown image ref (no caption); option B is text-only.
        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Which figure comes next?", "بيان",
                        List.of(new ParsedOption("![optA.png](optA.png)", "أ", 1, 0),
                                new ParsedOption("B", "ب", 2, 1)))),
                List.of(new ScoringSheetScale("ATD", null, ScoreMethod.SUM,
                        NormStrategyType.EMPIRICAL_PERCENTILE,
                        null, null, null, null, null, null, null, null, List.of(), null, false)),
                List.of(new ScoringSheetItem("Q1", "ATD", ScoreDirection.FORWARD,
                        ItemStrategyType.ANSWER_KEY_SINGLE, 1.0, null)),
                List.of(new AnswerKeyEntry("Q1", 2)));

        Map<String, byte[]> images = Map.of("optA.png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        importer.importPackage(
                new ImportPackageRequest("ATD", "desc", TestType.COGNITIVE, 600, null), pkg, images, UUID.randomUUID());

        ArgumentCaptor<AddQuestionRequest> qCap = ArgumentCaptor.forClass(AddQuestionRequest.class);
        verify(admin).addQuestion(eq(testId), qCap.capture(), any());
        CandidateAnswerDto optA = qCap.getValue().candidateAnswers().get(0);
        assertThat(optA.imageAssetId()).isEqualTo(assetId);
        assertThat(optA.label()).isEqualTo("");                 // markdown stripped to empty
        assertThat(optA.label()).doesNotContain("![");
        // text-only option carries no image
        CandidateAnswerDto optB = qCap.getValue().candidateAnswers().get(1);
        assertThat(optB.imageAssetId()).isNull();
        assertThat(optB.label()).isEqualTo("B");
    }
}
