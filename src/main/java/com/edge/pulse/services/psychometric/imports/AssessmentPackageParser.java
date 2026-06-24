package com.edge.pulse.services.psychometric.imports;

import com.edge.pulse.data.dto.psychometric.imports.*;
import com.edge.pulse.data.enums.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Pure stateless parser: turns three CSV strings (questions, answer_key, scoring_sheet)
 * into a validated {@link ParsedPackage}. No DB, no Spring beans, no entity imports.
 *
 * <p>Validation errors are collected into {@link ParseOutcome#errors()} — the parser never
 * throws on bad data; it builds best-effort and returns error list alongside.
 */
@Component
public class AssessmentPackageParser {

    /** Result of a parse operation: best-effort package plus any collected errors. */
    public record ParseOutcome(ParsedPackage pkg, List<ImportError> errors) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parse and validate the three CSVs.
     *
     * @param questionsCsv   CSV with columns: header, questionEN, questionAR, answerEN1, answerAR1, value1, …
     * @param answerKeyCsv   CSV with header row and a single ANS data row
     * @param scoringSheetCsv CSV with rowType column; "scale" and "item" rows
     * @return ParseOutcome with best-effort package and any errors
     */
    public ParseOutcome parse(String questionsCsv, String answerKeyCsv, String scoringSheetCsv) {
        List<ImportError> errors = new ArrayList<>();

        List<ParsedQuestion> questions = parseQuestions(questionsCsv, errors);
        List<ScoringSheetScale> scales = parseScoringSheetScales(scoringSheetCsv, errors);
        List<ScoringSheetItem> items = parseScoringSheetItems(scoringSheetCsv, errors);
        List<AnswerKeyEntry> answerKey = parseAnswerKey(answerKeyCsv, questions, errors);

        // Collect lookup sets for cross-reference validation
        Set<String> questionHeaders = new LinkedHashSet<>();
        for (ParsedQuestion q : questions) questionHeaders.add(q.header());

        Set<String> scaleNames = new LinkedHashSet<>();
        for (ScoringSheetScale s : scales) scaleNames.add(s.name());

        // Validate items: scaleName and questionHeader must exist
        for (int i = 0; i < items.size(); i++) {
            ScoringSheetItem item = items.get(i);
            String rowLabel = "item-row-" + (i + 1);
            if (item.scaleName() == null || item.scaleName().isBlank()
                    || !scaleNames.contains(item.scaleName())) {
                errors.add(new ImportError("scoring_sheet.csv", rowLabel, "scaleName",
                        "unknown scale: '" + item.scaleName() + "'"));
            }
            if (item.questionHeader() == null || item.questionHeader().isBlank()
                    || !questionHeaders.contains(item.questionHeader())) {
                errors.add(new ImportError("scoring_sheet.csv", rowLabel, "questionHeader",
                        "unknown question: '" + item.questionHeader() + "'"));
            }
        }

        // Validate PARAMETRIC scales must have non-null mean AND sd
        for (ScoringSheetScale scale : scales) {
            if (scale.normStrategy() == NormStrategyType.PARAMETRIC) {
                if (scale.mean() == null || scale.sd() == null) {
                    errors.add(new ImportError("scoring_sheet.csv", "scale:" + scale.name(),
                            scale.mean() == null ? "mean" : "sd",
                            "PARAMETRIC scale '" + scale.name() + "' requires both mean and sd"));
                }
            }
        }

        // Validate composite scales: all childScaleNames must exist
        for (ScoringSheetScale scale : scales) {
            if (scale.childScaleNames() != null) {
                for (String child : scale.childScaleNames()) {
                    if (!child.isBlank() && !scaleNames.contains(child)) {
                        errors.add(new ImportError("scoring_sheet.csv", "scale:" + scale.name(),
                                "childScales",
                                "unknown child scale: '" + child + "'"));
                    }
                }
            }
        }

        ParsedPackage pkg = new ParsedPackage(questions, scales, items, answerKey);
        return new ParseOutcome(pkg, Collections.unmodifiableList(errors));
    }

    // -------------------------------------------------------------------------
    // questions.csv
    // -------------------------------------------------------------------------

    private List<ParsedQuestion> parseQuestions(String csv, List<ImportError> errors) {
        List<ParsedQuestion> out = new ArrayList<>();
        List<Map<String, String>> rows = CsvReader.parse(csv);
        for (int r = 0; r < rows.size(); r++) {
            Map<String, String> row = rows.get(r);
            String header = row.getOrDefault("header", "").trim();
            String bodyEn = row.getOrDefault("questionEN", "").trim();
            String bodyAr = row.getOrDefault("questionAR", "").trim();
            String rowLabel = String.valueOf(r + 2); // 1-based + header row

            List<ParsedOption> options = new ArrayList<>();
            boolean optionError = false;
            for (int k = 1; ; k++) {
                String ansEn = row.getOrDefault("answerEN" + k, "").trim();
                if (ansEn.isBlank()) break; // no more options
                String ansAr = row.getOrDefault("answerAR" + k, "").trim();
                String valueStr = row.getOrDefault("value" + k, "").trim();
                if (valueStr.isBlank()) {
                    errors.add(new ImportError("questions.csv", rowLabel, "value" + k,
                            "blank value for option " + k + " on question '" + header + "'"));
                    optionError = true;
                    break;
                }
                int value;
                try {
                    value = Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    errors.add(new ImportError("questions.csv", rowLabel, "value" + k,
                            "non-numeric value '" + valueStr + "' for option " + k
                                    + " on question '" + header + "'"));
                    optionError = true;
                    break;
                }
                options.add(new ParsedOption(ansEn, ansAr, value, k - 1));
            }

            if (!optionError) {
                out.add(new ParsedQuestion(header, bodyEn, bodyAr, Collections.unmodifiableList(options)));
            }
        }
        return Collections.unmodifiableList(out);
    }

    // -------------------------------------------------------------------------
    // scoring_sheet.csv — scale rows
    // -------------------------------------------------------------------------

    private List<ScoringSheetScale> parseScoringSheetScales(String csv, List<ImportError> errors) {
        List<ScoringSheetScale> out = new ArrayList<>();
        List<Map<String, String>> rows = CsvReader.parse(csv);
        for (int r = 0; r < rows.size(); r++) {
            Map<String, String> row = rows.get(r);
            if (!"scale".equalsIgnoreCase(row.getOrDefault("rowType", "").trim())) continue;
            String rowLabel = "scale-row-" + (r + 1);
            String name = row.getOrDefault("name", "").trim();

            // Required-field validation (I4/M3): scale rows must carry a name and a scoreMethod.
            if (name.isBlank()) {
                errors.add(new ImportError("scoring_sheet.csv", rowLabel, "name",
                        "scale name is required"));
            }
            if (row.getOrDefault("scoreMethod", "").trim().isBlank()) {
                errors.add(new ImportError("scoring_sheet.csv", rowLabel, "scoreMethod",
                        "scoreMethod is required for scale rows"));
            }

            ScoreMethod scoreMethod = parseEnum(ScoreMethod.class, row, "scoreMethod", rowLabel,
                    "scoring_sheet.csv", errors);
            NormStrategyType normStrategy = parseEnum(NormStrategyType.class, row, "normStrategy",
                    rowLabel, "scoring_sheet.csv", errors);
            BigDecimal mean = parseBigDecimal(row, "mean", rowLabel, "scoring_sheet.csv", errors);
            BigDecimal sd = parseBigDecimal(row, "sd", rowLabel, "scoring_sheet.csv", errors);
            BigDecimal tFactor = parseBigDecimal(row, "tFactor", rowLabel, "scoring_sheet.csv", errors);
            BigDecimal tOffset = parseBigDecimal(row, "tOffset", rowLabel, "scoring_sheet.csv", errors);
            BigDecimal tClipLo = parseBigDecimal(row, "tClipLo", rowLabel, "scoring_sheet.csv", errors);
            BigDecimal tClipHi = parseBigDecimal(row, "tClipHi", rowLabel, "scoring_sheet.csv", errors);
            CompositeMethod compositeMethod = parseEnum(CompositeMethod.class, row, "compositeMethod",
                    rowLabel, "scoring_sheet.csv", errors);
            CompositeBasis compositeBasis = parseEnum(CompositeBasis.class, row, "compositeBasis",
                    rowLabel, "scoring_sheet.csv", errors);

            // childScales: semicolon-separated list; blank = empty list
            String childScalesRaw = row.getOrDefault("childScales", "").trim();
            List<String> childScaleNames;
            if (childScalesRaw.isBlank()) {
                childScaleNames = List.of();
            } else {
                childScaleNames = Arrays.stream(childScalesRaw.split(";"))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
            }

            Integer roundingScale = parseInteger(row, "roundingScale", rowLabel, "scoring_sheet.csv", errors);
            boolean restricted = "true".equalsIgnoreCase(row.getOrDefault("restricted", "").trim());
            String parentName = row.getOrDefault("parentName", "").trim();
            String parentNameVal = parentName.isBlank() ? null : parentName;

            out.add(new ScoringSheetScale(name, parentNameVal, scoreMethod, normStrategy,
                    mean, sd, tFactor, tOffset, tClipLo, tClipHi,
                    compositeMethod, compositeBasis, childScaleNames, roundingScale, restricted));
        }
        return Collections.unmodifiableList(out);
    }

    // -------------------------------------------------------------------------
    // scoring_sheet.csv — item rows
    // -------------------------------------------------------------------------

    private List<ScoringSheetItem> parseScoringSheetItems(String csv, List<ImportError> errors) {
        List<ScoringSheetItem> out = new ArrayList<>();
        List<Map<String, String>> rows = CsvReader.parse(csv);
        for (int r = 0; r < rows.size(); r++) {
            Map<String, String> row = rows.get(r);
            if (!"item".equalsIgnoreCase(row.getOrDefault("rowType", "").trim())) continue;
            String rowLabel = "item-row-" + (r + 1);

            String questionHeader = row.getOrDefault("questionHeader", "").trim();
            String scaleName = row.getOrDefault("scaleName", "").trim();
            ScoreDirection direction = parseEnum(ScoreDirection.class, row, "direction",
                    rowLabel, "scoring_sheet.csv", errors);
            if (direction == null && row.getOrDefault("direction", "").trim().isBlank()) {
                errors.add(new ImportError("scoring_sheet.csv", rowLabel, "direction",
                        "direction is required for item rows (FORWARD or REVERSE)"));
            }
            ItemStrategyType itemStrategy = parseEnum(ItemStrategyType.class, row, "itemStrategy",
                    rowLabel, "scoring_sheet.csv", errors);
            double weight = 1.0;
            String weightStr = row.getOrDefault("weight", "").trim();
            if (!weightStr.isBlank()) {
                try {
                    weight = Double.parseDouble(weightStr);
                } catch (NumberFormatException e) {
                    errors.add(new ImportError("scoring_sheet.csv", rowLabel, "weight",
                            "non-numeric weight '" + weightStr + "'"));
                }
            }
            String tagScaleName = row.getOrDefault("tagScaleName", "").trim();
            String tagScaleNameVal = tagScaleName.isBlank() ? null : tagScaleName;

            out.add(new ScoringSheetItem(questionHeader, scaleName, direction, itemStrategy,
                    weight, tagScaleNameVal));
        }
        return Collections.unmodifiableList(out);
    }

    // -------------------------------------------------------------------------
    // answer_key.csv
    // -------------------------------------------------------------------------

    private List<AnswerKeyEntry> parseAnswerKey(String csv, List<ParsedQuestion> questions,
                                                 List<ImportError> errors) {
        List<AnswerKeyEntry> out = new ArrayList<>();
        List<Map<String, String>> rows = CsvReader.parse(csv);
        // Build set of question headers for validation
        Set<String> questionHeaders = new HashSet<>();
        for (ParsedQuestion q : questions) questionHeaders.add(q.header());

        for (Map<String, String> row : rows) {
            String headerVal = row.getOrDefault("header", "").trim();
            if (!"ANS".equalsIgnoreCase(headerVal)) continue;
            // Every column except "header" that maps to a question header is an answer
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String colName = entry.getKey().trim();
                if ("header".equalsIgnoreCase(colName)) continue;
                String cell = entry.getValue().trim();
                if (cell.isBlank()) continue;
                if (!questionHeaders.contains(colName)) {
                    // Column is not a known question header — report error but keep parsing others
                    errors.add(new ImportError("answer_key.csv", "ANS", colName,
                            "answer key references unknown question '" + colName + "'"));
                    continue;
                }
                try {
                    int correctValue = Integer.parseInt(cell);
                    out.add(new AnswerKeyEntry(colName, correctValue));
                } catch (NumberFormatException e) {
                    errors.add(new ImportError("answer_key.csv", "ANS", colName,
                            "non-numeric answer value '" + cell + "' for question '" + colName + "'"));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    // -------------------------------------------------------------------------
    // Parsing helpers — collect errors, return null on blank/missing
    // -------------------------------------------------------------------------

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, Map<String, String> row,
                                             String column, String rowLabel,
                                             String file, List<ImportError> errors) {
        String raw = row.getOrDefault(column, "").trim();
        if (raw.isBlank()) return null;
        try {
            return Enum.valueOf(enumClass, raw);
        } catch (IllegalArgumentException e) {
            errors.add(new ImportError(file, rowLabel, column,
                    "unknown " + enumClass.getSimpleName() + " value '" + raw + "'"));
            return null;
        }
    }

    private BigDecimal parseBigDecimal(Map<String, String> row, String column,
                                        String rowLabel, String file, List<ImportError> errors) {
        String raw = row.getOrDefault(column, "").trim();
        if (raw.isBlank()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            errors.add(new ImportError(file, rowLabel, column,
                    "non-numeric value '" + raw + "' in column '" + column + "'"));
            return null;
        }
    }

    private Integer parseInteger(Map<String, String> row, String column,
                                  String rowLabel, String file, List<ImportError> errors) {
        String raw = row.getOrDefault(column, "").trim();
        if (raw.isBlank()) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            errors.add(new ImportError(file, rowLabel, column,
                    "non-integer value '" + raw + "' in column '" + column + "'"));
            return null;
        }
    }
}
