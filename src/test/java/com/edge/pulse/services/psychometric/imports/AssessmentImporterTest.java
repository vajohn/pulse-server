package com.edge.pulse.services.psychometric.imports;

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
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    @InjectMocks AssessmentImporter importer;

    // ── DTO factories (match the real record field order) ──────────────────────

    private static PsychometricTestDto testDto(UUID testId) {
        return new PsychometricTestDto(testId, UUID.randomUUID(), "ATP", "desc", null,
                "PERSONALITY", null, "DRAFT", 1, LocalDateTime.now(), 0, 0);
    }

    private static QuestionDto questionDto(UUID qId, UUID optId1, UUID optId2) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.CHOICE_SINGLE, 0,
                null, null,
                List.of(new CandidateAnswerDto(optId1, "A", "أ", 0),
                        new CandidateAnswerDto(optId2, "B", "ب", 1)),
                null, null, false, null, null, null, null, null, null, null);
    }

    private static QuestionDto questionDtoSingleOpt(UUID qId, UUID optId) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.SCALE, 0,
                null, null,
                List.of(new CandidateAnswerDto(optId, "A", "أ", 0)),
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
                new ImportPackageRequest("ATP", "desc", TestType.PERSONALITY, null), pkg, UUID.randomUUID());

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
                new ImportPackageRequest("CA", "desc", TestType.COGNITIVE, 600), pkg, UUID.randomUUID());

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
                new ImportPackageRequest("X", null, TestType.PERSONALITY, null), pkg, UUID.randomUUID()))
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
                new ImportPackageRequest("VIP", null, TestType.PERSONALITY, null), pkg, UUID.randomUUID());
        assertThat(res.success()).isTrue();
        assertThat(res.items()).isEqualTo(2);
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
                new ImportPackageRequest("X", null, TestType.PERSONALITY, null), pkg, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown scale");

        verify(admin, never()).saveScoringKey(any(), anyList(), any());
    }
}
