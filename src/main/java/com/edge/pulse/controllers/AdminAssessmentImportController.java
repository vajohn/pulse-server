package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.imports.ImportPackageRequest;
import com.edge.pulse.data.dto.psychometric.imports.ImportResultDto;
import com.edge.pulse.data.enums.TestType;
import com.edge.pulse.services.psychometric.imports.AssessmentImporter;
import com.edge.pulse.services.psychometric.imports.AssessmentPackageParser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Multipart endpoint to import a 3-CSV assessment package (questions + answer_key + scoring_sheet)
 * into a fully-configured {@code PsychometricTest}.
 *
 * <p>Refuse-partial: if the parser reports any errors the request is rejected with HTTP 422 and a
 * list of column-level {@code ImportError} entries. No DB writes occur until a clean parse.
 */
@RestController
@RequestMapping("/api/admin/psychometric")
public class AdminAssessmentImportController {

    private final AssessmentPackageParser parser;
    private final AssessmentImporter importer;

    public AdminAssessmentImportController(AssessmentPackageParser parser, AssessmentImporter importer) {
        this.parser = parser;
        this.importer = importer;
    }

    @PostMapping(value = "/import-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ASSESS_KEY_MANAGE') and hasAuthority('ASSESS_CREATE')")
    public ResponseEntity<ImportResultDto> importPackage(
            @RequestParam("questions") MultipartFile questions,
            @RequestParam("answerKey") MultipartFile answerKey,
            @RequestParam("scoringSheet") MultipartFile scoringSheet,
            @RequestParam("testName") String testName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("testType") TestType testType,
            @RequestParam(value = "timeLimitSecs", required = false) Integer timeLimitSecs,
            Authentication auth) throws Exception {

        AssessmentPackageParser.ParseOutcome outcome = parser.parse(
                new String(questions.getBytes(), StandardCharsets.UTF_8),
                new String(answerKey.getBytes(), StandardCharsets.UTF_8),
                new String(scoringSheet.getBytes(), StandardCharsets.UTF_8));

        if (!outcome.errors().isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(new ImportResultDto(false, null, 0, 0, 0, 0, outcome.errors()));
        }

        ImportResultDto result = importer.importPackage(
                new ImportPackageRequest(testName, description, testType, timeLimitSecs),
                outcome.pkg(),
                (UUID) auth.getPrincipal());

        return ResponseEntity.ok(result);
    }
}
