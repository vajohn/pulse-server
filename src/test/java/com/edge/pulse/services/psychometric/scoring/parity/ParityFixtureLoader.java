package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.data.enums.*;
import com.edge.pulse.services.psychometric.scoring.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads a Beacon Red parity fixture (config.json + responses.csv + expected.csv) and produces a
 * {@link ScoringInput} per user plus the gold STEN/T-score map per user/scale.
 *
 * <p>Deterministic synthetic UUIDs are derived from question/scale NAMES so item-&gt;scale and
 * answer-key wiring is stable across runs. No JPA, no Spring.
 */
public final class ParityFixtureLoader {

    /** Everything a parity test needs for one assessment. */
    public static final class Fixture {
        public final List<ScaleConfig> scales;
        public final List<ItemConfig> items;
        /** scaleName -> synthetic UUID */
        public final Map<String, UUID> scaleIdByName;
        /** userName -> (questionId -> ItemResponse) */
        public final Map<String, Map<UUID, ItemResponse>> responsesByUser;
        /** userName, in input order */
        public final List<String> userOrder;
        /** userName -> scaleName -> expected STEN */
        public final Map<String, Map<String, BigDecimal>> expectedSten;
        /** userName -> scaleName -> expected T-score (only scales that have a _Tscore column) */
        public final Map<String, Map<String, BigDecimal>> expectedT;

        Fixture(List<ScaleConfig> scales, List<ItemConfig> items, Map<String, UUID> scaleIdByName,
                Map<String, Map<UUID, ItemResponse>> responsesByUser, List<String> userOrder,
                Map<String, Map<String, BigDecimal>> expectedSten,
                Map<String, Map<String, BigDecimal>> expectedT) {
            this.scales = scales;
            this.items = items;
            this.scaleIdByName = scaleIdByName;
            this.responsesByUser = responsesByUser;
            this.userOrder = userOrder;
            this.expectedSten = expectedSten;
            this.expectedT = expectedT;
        }

        public ScoringInput inputFor(String userName) {
            return new ScoringInput(scales, items, responsesByUser.get(userName));
        }
    }

    private static final ObjectMapper M = new ObjectMapper();

    private ParityFixtureLoader() {}

    /** Deterministic UUID from a logical name (e.g. "scale:Agility" / "q:Q5"). */
    public static UUID synthId(String logicalName) {
        return UUID.nameUUIDFromBytes(logicalName.getBytes(StandardCharsets.UTF_8));
    }

    public static Fixture load(String dir) {
        try {
            JsonNode cfg = M.readTree(resource(dir + "/config.json"));
            if (cfg.has("subScales")) return loadCa(dir, cfg);
            String assessment = cfg.get("assessment").asText();
            String strategyStr = cfg.path("itemStrategy").asText("LIKERT_VALUE");
            ItemStrategyType strategy = ItemStrategyType.valueOf(strategyStr);

            BigDecimal tFactor = bd(cfg, "tFactor", "10");
            BigDecimal tOffset = bd(cfg, "tOffset", "50");
            BigDecimal tClipLo = bd(cfg, "tClipLo", "10");
            BigDecimal tClipHi = bd(cfg, "tClipHi", "120");

            // Likert recode params (feed value-offset into [min,max] recoded space)
            int likertMin = cfg.path("likert").path("min").asInt(0);
            int likertMax = cfg.path("likert").path("max").asInt(0);
            int likertOffset = cfg.path("likert").path("offset").asInt(0);
            int binaryMin = cfg.path("binary").path("min").asInt(1);
            int binaryMax = cfg.path("binary").path("max").asInt(2);

            Map<String, UUID> scaleIdByName = new LinkedHashMap<>();
            Map<String, String> parentOf = new HashMap<>(); // leaf scale name -> AGGREGATE_OF_ITEMS parent name
            List<ScaleConfig> scales = new ArrayList<>();
            List<ItemConfig> items = new ArrayList<>();
            // questionName -> direction; for precomputed scales we instead synthesize a value
            Map<String, ScoreDirection> questionDirection = new LinkedHashMap<>();
            Map<String, String> questionScale = new LinkedHashMap<>();
            List<String> precomputedScales = new ArrayList<>();
            Map<String, String> precomputedKind = new LinkedHashMap<>();

            for (JsonNode s : cfg.get("scales")) {
                String name = s.get("name").asText();
                UUID id = synthId("scale:" + assessment + ":" + name);
                scaleIdByName.put(name, id);
            }

            for (JsonNode s : cfg.get("scales")) {
                String name = s.get("name").asText();
                UUID id = scaleIdByName.get(name);
                ScoreMethod scoreMethod = ScoreMethod.valueOf(s.path("scoreMethod").asText("SUM"));
                NormConfig norm = new NormConfig(NormStrategyType.PARAMETRIC,
                        new BigDecimal(s.get("mean").asText()), new BigDecimal(s.get("sd").asText()),
                        tFactor, tOffset, tClipLo, tClipHi, null);
                Integer roundingScale = s.has("roundingScale") ? s.get("roundingScale").asInt() : null;

                if (s.has("composite")) {
                    CompositeMethod cm = CompositeMethod.valueOf(s.get("composite").asText());
                    List<UUID> childIds = new ArrayList<>();
                    if (s.has("children"))
                        for (JsonNode c : s.get("children")) childIds.add(scaleIdByName.get(c.asText()));
                    if (cm == CompositeMethod.AGGREGATE_OF_ITEMS) {
                        // children are leaf scales whose parentScaleId points here; raw = sum of children raw
                        scales.add(new ScaleConfig(id, name, null, scoreMethod, cm, null, childIds, norm, roundingScale));
                        // mark children's parent
                        for (JsonNode c : s.get("children")) {
                            // handled below when we rewrite leaves' parent
                            parentOf.put(c.asText(), name);
                        }
                    } else {
                        CompositeBasis basis = CompositeBasis.valueOf(s.path("basis").asText("STEN"));
                        scales.add(new ScaleConfig(id, name, null, scoreMethod, cm, basis, childIds, null, roundingScale));
                    }
                    continue;
                }

                if (s.has("precomputed")) {
                    precomputedScales.add(name);
                    precomputedKind.put(name, s.get("precomputed").asText());
                    scales.add(new ScaleConfig(id, name, null, scoreMethod, null, null, null, norm, roundingScale));
                    // one synthetic forward LIKERT item carries the precomputed raw value
                    String q = "PRECOMP:" + name;
                    questionScale.put(q, name);
                    questionDirection.put(q, ScoreDirection.FORWARD);
                    continue;
                }

                // leaf (parentScaleId wired in the rewrite pass below)
                scales.add(new ScaleConfig(id, name, null, scoreMethod, null, null, null, norm, roundingScale));
                if (s.has("normal"))
                    for (JsonNode q : s.get("normal")) {
                        questionScale.put(q.asText(), name);
                        questionDirection.put(q.asText(), ScoreDirection.FORWARD);
                    }
                if (s.has("reversed"))
                    for (JsonNode q : s.get("reversed")) {
                        questionScale.put(q.asText(), name);
                        questionDirection.put(q.asText(), ScoreDirection.REVERSE);
                    }
            }

            // Rewrite leaf scales that have an AGGREGATE_OF_ITEMS parent to set parentScaleId.
            List<ScaleConfig> rewritten = new ArrayList<>();
            for (ScaleConfig sc : scales) {
                String parent = parentOf.get(sc.name());
                if (parent != null && sc.compositeMethod() == null) {
                    rewritten.add(new ScaleConfig(sc.scaleId(), sc.name(), scaleIdByName.get(parent),
                            sc.scoreMethod(), null, null, null, sc.norm(), sc.compositeRoundingScale()));
                } else {
                    rewritten.add(sc);
                }
            }
            scales = rewritten;

            // Build ItemConfig list (questionId per question NAME).
            for (var e : questionScale.entrySet()) {
                String q = e.getKey();
                UUID qid = synthId("q:" + assessment + ":" + q);
                items.add(new ItemConfig(qid, scaleIdByName.get(e.getValue()), strategy,
                        questionDirection.get(q), 1.0, null, null, false, null));
            }

            // ---- responses ----
            List<String[]> rows = readCsv(dir + "/responses.csv");
            String[] header = rows.get(0);
            int userCol = indexOf(header, "userName");
            List<String> userOrder = new ArrayList<>();
            Map<String, Map<UUID, ItemResponse>> responsesByUser = new LinkedHashMap<>();

            for (int ri = 1; ri < rows.size(); ri++) {
                String[] row = rows.get(ri);
                if (row.length <= userCol || row[userCol].isBlank()) continue;
                String user = row[userCol];
                userOrder.add(user);
                Map<String, Integer> rawByQ = new LinkedHashMap<>();
                Map<UUID, ItemResponse> resp = new LinkedHashMap<>();
                for (int c = 0; c < header.length; c++) {
                    String col = header[c];
                    if (!col.startsWith("Q")) continue;
                    String cell = c < row.length ? row[c] : "";
                    Integer val = coerceInt(cell);
                    if (val == null) continue;
                    rawByQ.put(col, val);
                    if (!questionScale.containsKey(col)) continue; // question not used by any scale
                    UUID qid = synthId("q:" + assessment + ":" + col);
                    if (strategy == ItemStrategyType.LIKERT_VALUE) {
                        int v = val - likertOffset;
                        resp.put(qid, new ItemResponse(qid, v, likertMin, likertMax, null, null, null));
                    } else if (strategy == ItemStrategyType.BINARY_FORCED_CHOICE) {
                        resp.put(qid, new ItemResponse(qid, val, binaryMin, binaryMax, null, null, null));
                    } else {
                        throw new IllegalStateException("Unsupported strategy for CSV-value loading: " + strategy);
                    }
                }
                // precomputed scales (e.g. PTI Consistency)
                for (String name : precomputedScales) {
                    int raw = computePrecomputed(precomputedKind.get(name), rawByQ, likertOffset);
                    UUID qid = synthId("q:" + assessment + ":PRECOMP:" + name);
                    // forward LIKERT returns value as-is; min/max unused for forward
                    resp.put(qid, new ItemResponse(qid, raw, 0, 0, null, null, null));
                }
                responsesByUser.put(user, resp);
            }

            // ---- expected ----
            Map<String, Map<String, BigDecimal>> expSten = new LinkedHashMap<>();
            Map<String, Map<String, BigDecimal>> expT = new LinkedHashMap<>();
            List<String[]> exp = readCsv(dir + "/expected.csv");
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

            return new Fixture(scales, items, scaleIdByName, responsesByUser, userOrder, expSten, expT);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load parity fixture: " + dir, ex);
        }
    }

    /**
     * CA (cognitive) loader: answer-key sub-scales merged across multiple response files plus the
     * CA_overall (STEN-mean) and IQ_overall (T-mean) composites. One {@link ItemConfig} per
     * sub-scale question with its keyed option as correctAnswerId; per-user {@link ItemResponse}
     * carries the chosen option's synthetic id in selectedAnswerIds.
     */
    private static Fixture loadCa(String dir, JsonNode cfg) throws Exception {
        String assessment = cfg.get("assessment").asText();
        BigDecimal tFactor = bd(cfg, "tFactor", "15");
        BigDecimal tOffset = bd(cfg, "tOffset", "100");
        BigDecimal tClipLo = bd(cfg, "tClipLo", "40");
        BigDecimal tClipHi = bd(cfg, "tClipHi", "160");

        Map<String, UUID> scaleIdByName = new LinkedHashMap<>();
        List<ScaleConfig> scales = new ArrayList<>();
        List<ItemConfig> items = new ArrayList<>();
        // per-user, accumulate responses across all sub-scale files
        Map<String, Map<UUID, ItemResponse>> responsesByUser = new LinkedHashMap<>();
        LinkedHashSet<String> userSet = new LinkedHashSet<>();

        for (JsonNode ss : cfg.get("subScales")) {
            String name = ss.get("name").asText();
            UUID scaleId = synthId("scale:" + assessment + ":" + name);
            scaleIdByName.put(name, scaleId);
            int nq = ss.get("questions").asInt();
            int answerStartCol = ss.path("answerStartCol").asInt(0);
            NormConfig norm = new NormConfig(NormStrategyType.PARAMETRIC,
                    new BigDecimal(ss.get("mean").asText()), new BigDecimal(ss.get("sd").asText()),
                    tFactor, tOffset, tClipLo, tClipHi, null);
            scales.add(new ScaleConfig(scaleId, name, null, ScoreMethod.SUM, null, null, null, norm, null));

            // answer key (ANS row, Q1 at column index 3)
            List<String[]> keyRows = readCsv(dir + "/" + ss.get("key").asText());
            String[] ansRow = keyRows.get(1); // row after header is ANS
            int[] keyVals = new int[nq];
            for (int i = 0; i < nq; i++) keyVals[i] = Integer.parseInt(ansRow[3 + i].trim());

            // build items + correctAnswerId per question
            for (int i = 0; i < nq; i++) {
                String q = name + ":Q" + (i + 1);
                UUID qid = synthId("q:" + assessment + ":" + q);
                UUID correct = synthId("opt:" + assessment + ":" + q + ":" + keyVals[i]);
                items.add(new ItemConfig(qid, scaleId, ItemStrategyType.ANSWER_KEY_SINGLE,
                        ScoreDirection.FORWARD, 1.0, correct, null, false, null));
            }

            // responses: locate the answer block (first nq Q-columns after skipping answerStartCol leading Q-cols)
            List<String[]> rows = readCsv(dir + "/" + ss.get("responses").asText());
            String[] header = rows.get(0);
            int userCol = indexOf(header, "userName");
            List<Integer> qColIdx = new ArrayList<>();
            for (int c = 0; c < header.length; c++) if (header[c].startsWith("Q")) qColIdx.add(c);
            // skip the leading answerStartCol Q-columns (e.g. CA.b duplicate timer Q1,Q2), then take nq
            List<Integer> answerCols = qColIdx.subList(answerStartCol, answerStartCol + nq);

            for (int ri = 1; ri < rows.size(); ri++) {
                String[] row = rows.get(ri);
                if (row.length <= userCol || row[userCol].isBlank()) continue;
                String user = row[userCol];
                userSet.add(user);
                Map<UUID, ItemResponse> resp = responsesByUser.computeIfAbsent(user, k -> new LinkedHashMap<>());
                for (int i = 0; i < nq; i++) {
                    int col = answerCols.get(i);
                    Integer val = col < row.length ? coerceInt(row[col]) : null;
                    String q = name + ":Q" + (i + 1);
                    UUID qid = synthId("q:" + assessment + ":" + q);
                    // unanswered/NaN -> a value that never matches the key (so scores 0, mirroring replace NaN->0)
                    UUID chosen = synthId("opt:" + assessment + ":" + q + ":" + (val == null ? "__NA__" : String.valueOf(val)));
                    resp.put(qid, new ItemResponse(qid, null, null, null, List.of(chosen), null, null));
                }
            }
        }

        // composites
        for (JsonNode cj : cfg.get("composites")) {
            String name = cj.get("name").asText();
            UUID id = synthId("scale:" + assessment + ":" + name);
            scaleIdByName.put(name, id);
            CompositeMethod cm = CompositeMethod.valueOf(cj.get("method").asText());
            CompositeBasis basis = CompositeBasis.valueOf(cj.get("basis").asText());
            List<UUID> childIds = new ArrayList<>();
            for (JsonNode c : cj.get("children")) childIds.add(scaleIdByName.get(c.asText()));
            Integer roundingScale = cj.has("roundingScale") ? cj.get("roundingScale").asInt() : null;
            scales.add(new ScaleConfig(id, name, null, ScoreMethod.SUM, cm, basis, childIds, null, roundingScale));
        }

        // expected
        Map<String, Map<String, BigDecimal>> expSten = new LinkedHashMap<>();
        Map<String, Map<String, BigDecimal>> expT = new LinkedHashMap<>();
        List<String[]> exp = readCsv(dir + "/expected.csv");
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
                if (col.endsWith("_Sten"))
                    sten.put(col.substring(0, col.length() - "_Sten".length()), new BigDecimal(cell.trim()));
                else if (col.endsWith("_Tscore"))
                    t.put(col.substring(0, col.length() - "_Tscore".length()), new BigDecimal(cell.trim()));
            }
            expSten.put(user, sten);
            expT.put(user, t);
        }

        return new Fixture(scales, items, scaleIdByName, responsesByUser,
                new ArrayList<>(userSet), expSten, expT);
    }

    /** PTI Consistency validity scale: sum of abs differences over recoded item values. */
    private static int computePrecomputed(String kind, Map<String, Integer> rawByQ, int offset) {
        if ("PTI_CONSISTENCY".equals(kind)) {
            // PTI Consistency uses NORMAL-recoded values: recoded = raw - offset (offset=1 -> 0..3)
            java.util.function.Function<String, Integer> q = name -> rawByQ.get(name) - offset;
            int q1 = q.apply("Q1"), q7 = q.apply("Q7"), q13 = q.apply("Q13"), q19 = q.apply("Q19"),
                    q25 = q.apply("Q25"), q149 = q.apply("Q149"), q155 = q.apply("Q155"),
                    q161 = q.apply("Q161"), q167 = q.apply("Q167");
            return Math.abs((3 - q1) - q13)
                    + Math.abs((3 - q1) - q161)
                    + Math.abs((3 - q13) - q25)
                    + Math.abs((3 - q149) - q7)
                    + Math.abs(q1 - q149)
                    + Math.abs(q1 - q7)
                    + Math.abs(q13 - q155)
                    + Math.abs(q161 - q149)
                    + Math.abs(q161 - q7)
                    + Math.abs(q167 - q19)
                    + Math.abs(q25 - q155);
        }
        throw new IllegalArgumentException("Unknown precomputed kind: " + kind);
    }

    /** Mirror the notebooks' .str.split('.').str[0] integer coercion ("1.0" -> 1). */
    static Integer coerceInt(String cell) {
        if (cell == null) return null;
        String s = cell.trim();
        if (s.isEmpty()) return null;
        int dot = s.indexOf('.');
        if (dot >= 0) s = s.substring(0, dot);
        if (s.isEmpty()) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal bd(JsonNode cfg, String field, String dflt) {
        return new BigDecimal(cfg.has(field) ? cfg.get(field).asText() : dflt);
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].trim().equals(v)) return i;
        throw new IllegalArgumentException("Column not found: " + v);
    }

    static List<String[]> readCsv(String resource) throws Exception {
        List<String[]> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource(resource), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                out.add(splitCsv(line));
            }
        }
        return out;
    }

    /** Minimal CSV split (handles simple quoted fields). */
    static String[] splitCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (inQ) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQ = false;
                } else cur.append(ch);
            } else {
                if (ch == '"') inQ = true;
                else if (ch == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(ch);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static InputStream resource(String path) {
        InputStream in = ParityFixtureLoader.class.getClassLoader()
                .getResourceAsStream("psychometric/parity/" + path);
        if (in == null) throw new IllegalStateException("Missing test resource: psychometric/parity/" + path);
        return in;
    }
}
