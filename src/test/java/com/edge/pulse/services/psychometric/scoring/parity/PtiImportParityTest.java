package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.data.dto.psychometric.imports.*;
import com.edge.pulse.data.enums.*;
import com.edge.pulse.services.psychometric.imports.AssessmentPackageParser;
import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import com.edge.pulse.services.psychometric.scoring.model.*;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1B / Task 8 — IMPORT-FORMAT parity for PTI.
 *
 * <p>{@link PtiParityTest} already proves the pure {@link ScoringCalculator} reproduces Beacon Red's
 * gold PTI STEN/T-scores when fed a {@link ScoringInput} built directly from {@code config.json}.
 * This test proves the §9 assessment-package CSV FORMAT (questions.csv + scoring_sheet.csv) faithfully
 * carries that same config: we parse the package with {@link AssessmentPackageParser}, build a
 * {@link ScoringInput} <em>only</em> from the {@link ParsedPackage} (no peeking at config.json), run the
 * unmodified calculator over {@code responses.csv}, and assert every package-representable scale's
 * STEN/T matches {@code expected.csv} to the same 1-dp precision the 1A harness uses.
 *
 * <p><b>Proof approach: calculator-based</b> (not structural). The ParsedPackage → ScoringInput adapter
 * below mirrors {@link ParityFixtureLoader}'s name→UUID mapping and ScaleConfig/ItemConfig/NormConfig
 * construction, but is driven exclusively by the parsed CSV records.
 *
 * <p><b>§9 format gap (documented, see {@code reportFindings} in the task report):</b> the PTI
 * {@code Consistency} validity scale is a <em>precomputed</em> derived statistic
 * ({@code PTI_CONSISTENCY}: a sum of absolute differences across a fixed set of items). The §9 scoring
 * sheet can only express per-item FORWARD/REVERSE LIKERT contributions, so it cannot represent a derived
 * validity formula. The package therefore intentionally omits Consistency, and this test excludes it from
 * the comparison. All 13 other scored scales (9 leaf + the 4 CONTROL/COMMITMENT/CHALLENGE/CONFIDENCE
 * AGGREGATE_OF_ITEMS composites) are fully representable and are asserted (STEN + T each).
 */
class PtiImportParityTest {

    private static final String ASSESSMENT = "PTI";
    private static final String DIR = "pti";

    /** Scales the §9 format cannot represent (derived/precomputed validity). Excluded from parity. */
    private static final Set<String> UNREPRESENTABLE = Set.of("Consistency");

    // Likert recode params — fixed by the PTI package contract (values 1..4 -> recoded 0..3).
    private static final int LIKERT_MIN = 0;
    private static final int LIKERT_MAX = 3;
    private static final int LIKERT_OFFSET = 1;

    @Test
    void packageImportReproducesVendorStenAndTscores() throws Exception {
        // 1. Parse the authored package.
        AssessmentPackageParser parser = new AssessmentPackageParser();
        AssessmentPackageParser.ParseOutcome outcome = parser.parse(
                read("questions.csv"), read("answer_key.csv"), read("scoring_sheet.csv"));

        assertThat(outcome.errors())
                .as("PTI package must parse with zero errors")
                .isEmpty();

        ParsedPackage pkg = outcome.pkg();

        // 2. Adapter: build ScoringInput config purely from the ParsedPackage.
        Map<String, UUID> scaleIdByName = new LinkedHashMap<>();
        for (ScoringSheetScale s : pkg.scales()) {
            scaleIdByName.put(s.name(), synthScaleId(s.name()));
        }

        // parentName on a leaf scale row designates its AGGREGATE_OF_ITEMS composite parent.
        List<ScaleConfig> scales = new ArrayList<>();
        for (ScoringSheetScale s : pkg.scales()) {
            UUID id = scaleIdByName.get(s.name());
            NormConfig norm = (s.normStrategy() == NormStrategyType.PARAMETRIC)
                    ? new NormConfig(NormStrategyType.PARAMETRIC, s.mean(), s.sd(),
                            s.tFactor(), s.tOffset(), s.tClipLo(), s.tClipHi(), null)
                    : null;

            if (s.compositeMethod() != null) {
                List<UUID> childIds = new ArrayList<>();
                for (String child : s.childScaleNames()) childIds.add(scaleIdByName.get(child));
                scales.add(new ScaleConfig(id, s.name(), null, s.scoreMethod(),
                        s.compositeMethod(), s.compositeBasis(), childIds, norm, s.roundingScale()));
            } else {
                UUID parentId = (s.parentName() == null) ? null : scaleIdByName.get(s.parentName());
                scales.add(new ScaleConfig(id, s.name(), parentId, s.scoreMethod(),
                        null, null, null, norm, s.roundingScale()));
            }
        }

        List<ItemConfig> items = new ArrayList<>();
        for (ScoringSheetItem it : pkg.items()) {
            UUID qid = synthQuestionId(it.questionHeader());
            items.add(new ItemConfig(qid, scaleIdByName.get(it.scaleName()), it.itemStrategy(),
                    it.direction(), it.weight(), null, null, false, null));
        }

        // 3. Build per-user responses from responses.csv (recode 1..4 -> 0..3).
        Set<String> usedHeaders = new HashSet<>();
        for (ScoringSheetItem it : pkg.items()) usedHeaders.add(it.questionHeader());

        List<String[]> rows = ParityFixtureLoader.readCsv(DIR + "/responses.csv");
        String[] header = rows.get(0);
        int userCol = indexOf(header, "userName");
        List<String> userOrder = new ArrayList<>();
        Map<String, Map<UUID, ItemResponse>> responsesByUser = new LinkedHashMap<>();

        for (int ri = 1; ri < rows.size(); ri++) {
            String[] row = rows.get(ri);
            if (row.length <= userCol || row[userCol].isBlank()) continue;
            String user = row[userCol];
            userOrder.add(user);
            Map<UUID, ItemResponse> resp = new LinkedHashMap<>();
            for (int c = 0; c < header.length; c++) {
                String col = header[c];
                if (!usedHeaders.contains(col)) continue;
                Integer val = ParityFixtureLoader.coerceInt(c < row.length ? row[c] : "");
                if (val == null) continue;
                UUID qid = synthQuestionId(col);
                int recoded = val - LIKERT_OFFSET;
                resp.put(qid, new ItemResponse(qid, recoded, LIKERT_MIN, LIKERT_MAX, null, null, null));
            }
            responsesByUser.put(user, resp);
        }

        // 4. Load gold expected.csv (STEN + T) per user/scale.
        Map<String, Map<String, BigDecimal>> expSten = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> expT = new LinkedHashMap<>();
        loadExpected(expSten, expT);

        // 5. Run the unmodified calculator and diff vs gold (excluding unrepresentable scales).
        ScoringCalculator calc = new ScoringCalculator();
        int comparisons = 0;
        StringBuilder failures = new StringBuilder();

        for (String user : userOrder) {
            ScoringOutput out = calc.calculate(
                    new ScoringInput(scales, items, responsesByUser.get(user)));
            Map<UUID, ScaleScoreResult> byId = new LinkedHashMap<>();
            out.scaleScores().forEach(r -> byId.put(r.scaleId(), r));

            Map<String, BigDecimal> sten = expSten.get(user);
            Map<String, BigDecimal> t = expT.get(user);
            Set<String> allScales = new LinkedHashSet<>(sten.keySet());
            allScales.addAll(t.keySet());

            for (String scale : allScales) {
                if (UNREPRESENTABLE.contains(scale)) continue;
                UUID id = scaleIdByName.get(scale);
                assertThat(id).as("package defines scale %s", scale).isNotNull();
                ScaleScoreResult r = byId.get(id);
                assertThat(r).as("engine produced result for %s/%s", user, scale).isNotNull();

                BigDecimal es = sten.get(scale);
                if (es != null) {
                    comparisons++;
                    if (r.stenScore() == null || r.stenScore().compareTo(es) != 0) {
                        failures.append(String.format("%s %s STEN: got %s expected %s%n",
                                user, scale, r.stenScore(), es));
                    }
                }
                BigDecimal et = t.get(scale);
                if (et != null) {
                    comparisons++;
                    if (r.tScore() == null || r.tScore().compareTo(et) != 0) {
                        failures.append(String.format("%s %s T: got %s expected %s%n",
                                user, scale, r.tScore(), et));
                    }
                }
            }
        }

        System.out.printf("PTI import-parity: %d users, %d scale comparisons (Consistency excluded — §9 gap)%n",
                userOrder.size(), comparisons);
        assertThat(failures.toString())
                .as("PTI import-parity mismatches (got vs expected)")
                .isEmpty();
        // 4 users x (12 scales: 8 leaf-with-T + Stress_Tolerance + 4 composites = 12 scales,
        // each STEN+T) — sanity floor so the test can never silently compare nothing.
        assertThat(comparisons).isGreaterThanOrEqualTo(4 * 12 * 2);
    }

    // --- adapter helpers: same naming scheme as ParityFixtureLoader so wiring is stable ---

    private static UUID synthScaleId(String name) {
        return ParityFixtureLoader.synthId("scale:" + ASSESSMENT + ":" + name);
    }

    private static UUID synthQuestionId(String header) {
        return ParityFixtureLoader.synthId("q:" + ASSESSMENT + ":" + header);
    }

    private void loadExpected(Map<String, Map<String, BigDecimal>> expSten,
                              Map<String, Map<String, BigDecimal>> expT) throws Exception {
        List<String[]> exp = ParityFixtureLoader.readCsv(DIR + "/expected.csv");
        String[] eh = exp.get(0);
        int euser = indexOf(eh, "userName");
        for (int ri = 1; ri < exp.size(); ri++) {
            String[] row = exp.get(ri);
            if (row.length <= euser || row[euser].isBlank()) continue;
            String user = row[euser];
            Map<String, BigDecimal> sten = new LinkedHashMap<>();
            Map<String, BigDecimal> t = new LinkedHashMap<>();
            for (int c = 0; c < eh.length; c++) {
                String col = eh[c];
                String cell = c < row.length ? row[c] : "";
                if (cell == null || cell.isBlank()) continue;
                if (col.endsWith("_Sten")) {
                    sten.put(col.substring(0, col.length() - "_Sten".length()), new BigDecimal(cell.trim()));
                } else if (col.endsWith("_Tscore")) {
                    t.put(col.substring(0, col.length() - "_Tscore".length()), new BigDecimal(cell.trim()));
                }
            }
            expSten.put(user, sten);
            expT.put(user, t);
        }
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].trim().equals(v)) return i;
        throw new IllegalArgumentException("Column not found: " + v);
    }

    private static String read(String name) throws Exception {
        try (InputStream in = PtiImportParityTest.class.getClassLoader()
                .getResourceAsStream("psychometric/parity/" + DIR + "/" + name)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
