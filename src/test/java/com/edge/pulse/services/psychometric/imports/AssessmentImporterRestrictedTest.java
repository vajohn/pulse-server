package com.edge.pulse.services.psychometric.imports;

import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.dto.psychometric.CreateScaleRequest;
import com.edge.pulse.data.dto.psychometric.PsychometricScaleDto;
import com.edge.pulse.data.dto.psychometric.PsychometricTestDto;
import com.edge.pulse.data.dto.psychometric.imports.ImportPackageRequest;
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
import com.edge.pulse.services.psychometric.assets.AssetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the scoring-sheet {@code restricted} flag (D3/§6.3) is propagated onto the created
 * scale via {@link CreateScaleRequest#restricted()}. CWB/validity scales must be excludable from
 * analytics by data, never by name-matching.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentImporterRestrictedTest {

    @Mock PsychometricAdminService admin;
    @Mock AssetService assetService;
    @InjectMocks AssessmentImporter importer;

    private static PsychometricTestDto testDto(UUID testId) {
        return new PsychometricTestDto(testId, UUID.randomUUID(), "ATP", "desc", null,
                "PERSONALITY", null, "DRAFT", 1, LocalDateTime.now(), 0, 0, null, null, null, null);
    }

    private static QuestionDto scaleQuestionDto(UUID qId, UUID optId) {
        return new QuestionDto(qId, "Stmt", "بيان", QuestionType.SCALE, 0,
                null, null,
                List.of(new CandidateAnswerDto(optId, "A", "أ", 0)),
                null, null, false, null, null, null, null, null, null, null);
    }

    private static PsychometricScaleDto scaleDto(UUID scaleId, String name) {
        return new PsychometricScaleDto(scaleId, UUID.randomUUID(), null, name, null,
                "SUM", 0, null, null, null);
    }

    private static ScoringSheetScale sheetScale(String name, boolean restricted) {
        return new ScoringSheetScale(name, null, ScoreMethod.SUM, NormStrategyType.EMPIRICAL_PERCENTILE,
                null, null, null, null, null, null, null, null, List.of(), null, restricted);
    }

    @Test
    void restrictedFlag_isPropagatedToCreatedScale() {
        UUID testId = UUID.randomUUID();
        UUID qId = UUID.randomUUID();
        UUID optId = UUID.randomUUID();

        when(admin.createTest(any(), any())).thenReturn(testDto(testId));
        when(admin.addQuestion(eq(testId), any(), any())).thenReturn(scaleQuestionDto(qId, optId));
        when(admin.createScale(eq(testId), any(), any()))
                .thenReturn(scaleDto(UUID.randomUUID(), "stub"));

        ParsedPackage pkg = new ParsedPackage(
                List.of(new ParsedQuestion("Q1", "Stmt", "بيان",
                        List.of(new ParsedOption("1", "١", 1, 0),
                                new ParsedOption("2", "٢", 2, 1),
                                new ParsedOption("3", "٣", 3, 2)))),
                List.of(sheetScale("Dominance", false),
                        sheetScale("Manipulativeness", true)),
                List.of(new ScoringSheetItem("Q1", "Dominance", ScoreDirection.FORWARD,
                        ItemStrategyType.LIKERT_VALUE, 1.0, null)),
                List.of());

        importer.importPackage(
                new ImportPackageRequest("ATP", "desc", TestType.PERSONALITY, null, null),
                pkg, UUID.randomUUID());

        ArgumentCaptor<CreateScaleRequest> cap = ArgumentCaptor.forClass(CreateScaleRequest.class);
        verify(admin, times(2)).createScale(eq(testId), cap.capture(), any());

        Map<String, Boolean> restrictedByName = cap.getAllValues().stream()
                .collect(java.util.stream.Collectors.toMap(CreateScaleRequest::name,
                        CreateScaleRequest::restricted));

        assertThat(restrictedByName).containsEntry("Dominance", false);
        assertThat(restrictedByName).containsEntry("Manipulativeness", true);
    }
}
