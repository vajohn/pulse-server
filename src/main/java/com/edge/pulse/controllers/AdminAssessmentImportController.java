package com.edge.pulse.controllers;

import com.edge.pulse.data.dto.psychometric.imports.ImportError;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
            @RequestParam(value = "images", required = false) MultipartFile images,
            Authentication auth) throws Exception {

        AssessmentPackageParser.ParseOutcome outcome = parser.parse(
                new String(questions.getBytes(), StandardCharsets.UTF_8),
                new String(answerKey.getBytes(), StandardCharsets.UTF_8),
                new String(scoringSheet.getBytes(), StandardCharsets.UTF_8));

        if (!outcome.errors().isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                    .body(new ImportResultDto(false, null, 0, 0, 0, 0, outcome.errors()));
        }

        Map<String, byte[]> imageBytes;
        try {
            imageBytes = (images != null && !images.isEmpty()) ? unzipImages(images) : Map.of();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.unprocessableEntity()
                    .body(new ImportResultDto(false, null, 0, 0, 0, 0,
                            List.of(new ImportError("images", "-", "-", ex.getMessage()))));
        }

        ImportResultDto result;
        try {
            result = importer.importPackage(
                    new ImportPackageRequest(testName, description, testType, timeLimitSecs, null),
                    outcome.pkg(),
                    imageBytes,
                    (UUID) auth.getPrincipal());
        } catch (IllegalArgumentException ex) {
            // Importer-side runtime validation failure (e.g. unresolved reference): return the
            // same 422 error envelope as a parse failure so the client sees one consistent shape.
            return ResponseEntity.unprocessableEntity()
                    .body(new ImportResultDto(false, null, 0, 0, 0, 0,
                            List.of(new ImportError("package", "-", "-", ex.getMessage()))));
        }

        return ResponseEntity.ok(result);
    }

    private static final int MAX_ENTRY_BYTES  = 5 * 1024 * 1024;   // per-image 5 MB guard (§10)
    private static final long MAX_TOTAL_BYTES  = 100L * 1024 * 1024; // aggregate 100 MB cap
    private static final int  MAX_ENTRY_COUNT  = 500;                 // aggregate entry-count cap

    /**
     * Unzips an uploaded image set into a {@code filename -> bytes} map. Skips directories; accepts
     * only {@code .png}/{@code .jpg}/{@code .jpeg}; caps each entry at 5 MB. Caps total extracted
     * bytes at 100 MB and total entries at 500. The map key is the entry's base filename (no path)
     * — it is matched against markdown alt-text in the importer.
     */
    private Map<String, byte[]> unzipImages(MultipartFile images) throws Exception {
        Map<String, byte[]> out = new HashMap<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(images.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRY_COUNT) {
                    zis.closeEntry();
                    throw new IllegalArgumentException("image archive too large");
                }
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String name = entry.getName();
                int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                String base = slash >= 0 ? name.substring(slash + 1) : name;
                String lower = base.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                    zis.closeEntry();
                    continue;
                }
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int read;
                int entryTotal = 0;
                while ((read = zis.read(chunk)) != -1) {
                    entryTotal += read;
                    if (entryTotal > MAX_ENTRY_BYTES) {
                        zis.closeEntry();
                        throw new IllegalArgumentException("Image " + base + " exceeds " + MAX_ENTRY_BYTES + " bytes");
                    }
                    buf.write(chunk, 0, read);
                }
                totalBytes += entryTotal;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    zis.closeEntry();
                    throw new IllegalArgumentException("image archive too large");
                }
                if (buf.size() > 0) {
                    out.put(base, buf.toByteArray());
                }
                zis.closeEntry();
            }
        }
        return out;
    }
}
