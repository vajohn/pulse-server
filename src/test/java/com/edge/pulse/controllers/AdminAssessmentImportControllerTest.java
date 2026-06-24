package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.imports.ImportResultDto;
import com.edge.pulse.services.psychometric.imports.AssessmentImporter;
import com.edge.pulse.services.psychometric.imports.AssessmentPackageParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc tests for {@link AdminAssessmentImportController}.
 *
 * <p>{@code @PreAuthorize} is NOT enforced in standalone setup — these tests verify HTTP mapping,
 * parser integration, and refuse-partial behaviour. A real {@link AssessmentPackageParser} is used
 * so validation errors come from the actual parser logic; {@link AssessmentImporter} is mocked.
 */
class AdminAssessmentImportControllerTest {

    MockMvc mvc;
    AssessmentImporter importer = mock(AssessmentImporter.class);
    AssessmentPackageParser parser = new AssessmentPackageParser();

    // Shared valid CSV bodies ---------------------------------------------------

    private static final String VALID_QUESTIONS =
            "header,questionEN,questionAR,answerEN1,answerAR1,value1,answerEN2,answerAR2,value2\n" +
            "Q1,S,ب,A,أ,1,B,ب,2\n";

    private static final String VALID_ANSWER_KEY =
            "header,Q1\n" +
            "ANS,2\n";

    private static final String VALID_SCORING_SHEET =
            "rowType,name,parentName,scoreMethod,normStrategy,mean,sd,tFactor,tOffset,tClipLo,tClipHi," +
            "compositeMethod,compositeBasis,childScales,roundingScale,restricted," +
            "questionHeader,scaleName,direction,itemStrategy,weight,tagScaleName\n" +
            "scale,Agility,,SUM,PARAMETRIC,7.92,3.10,10,50,10,120,,,,,,,,,,,\n" +
            "item,,,,,,,,,,,,,,,,Q1,Agility,FORWARD,BINARY_FORCED_CHOICE,1,\n";

    @BeforeEach
    void setUp() {
        var controller = new AdminAssessmentImportController(parser, importer);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void import_validPackage_returns200() throws Exception {
        when(importer.importPackage(any(), any(), any()))
                .thenReturn(new ImportResultDto(true, UUID.randomUUID(), 1, 1, 1, 1, List.of()));

        var q = new MockMultipartFile("questions", "questions.csv", "text/csv",
                VALID_QUESTIONS.getBytes());
        var k = new MockMultipartFile("answerKey", "answer_key.csv", "text/csv",
                VALID_ANSWER_KEY.getBytes());
        var s = new MockMultipartFile("scoringSheet", "scoring_sheet.csv", "text/csv",
                VALID_SCORING_SHEET.getBytes());

        mvc.perform(multipart("/api/admin/psychometric/import-package")
                        .file(q).file(k).file(s)
                        .param("testName", "ATP")
                        .param("testType", "PERSONALITY")
                        .principal(new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID(), null, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // -------------------------------------------------------------------------
    // Refuse-partial: item references unknown scale -> 422, importer never called
    // -------------------------------------------------------------------------

    @Test
    void import_badPackage_returns422WithErrors() throws Exception {
        // Scoring sheet item references a scale that is not defined in any scale row
        String badScoringSheet =
                "rowType,name,scoreMethod,normStrategy,questionHeader,scaleName,direction,itemStrategy,weight\n" +
                "item,,,,Q1,Nonexistent,FORWARD,LIKERT_VALUE,1\n";

        var q = new MockMultipartFile("questions", "questions.csv", "text/csv",
                "header,questionEN\nQ1,S\n".getBytes());
        var k = new MockMultipartFile("answerKey", "answer_key.csv", "text/csv",
                "header,Q1\nANS,2\n".getBytes());
        var s = new MockMultipartFile("scoringSheet", "scoring_sheet.csv", "text/csv",
                badScoringSheet.getBytes());

        mvc.perform(multipart("/api/admin/psychometric/import-package")
                        .file(q).file(k).file(s)
                        .param("testName", "X")
                        .param("testType", "PERSONALITY")
                        .principal(new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID(), null, List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors").isArray());

        verify(importer, never()).importPackage(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Importer runtime failure (IllegalArgumentException) -> 422 with errors (I1)
    // -------------------------------------------------------------------------

    @Test
    void import_importerThrowsIllegalArgument_returns422WithErrors() throws Exception {
        when(importer.importPackage(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("unresolved tag scale reference"));

        var q = new MockMultipartFile("questions", "questions.csv", "text/csv",
                VALID_QUESTIONS.getBytes());
        var k = new MockMultipartFile("answerKey", "answer_key.csv", "text/csv",
                VALID_ANSWER_KEY.getBytes());
        var s = new MockMultipartFile("scoringSheet", "scoring_sheet.csv", "text/csv",
                VALID_SCORING_SHEET.getBytes());

        mvc.perform(multipart("/api/admin/psychometric/import-package")
                        .file(q).file(k).file(s)
                        .param("testName", "ATP")
                        .param("testType", "PERSONALITY")
                        .principal(new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID(), null, List.of())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].message").value("unresolved tag scale reference"));
    }
}
