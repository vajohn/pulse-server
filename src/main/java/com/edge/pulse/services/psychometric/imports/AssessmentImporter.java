package com.edge.pulse.services.psychometric.imports;

import com.edge.pulse.data.dto.AddQuestionRequest;
import com.edge.pulse.data.dto.CandidateAnswerDto;
import com.edge.pulse.data.dto.QuestionDto;
import com.edge.pulse.data.dto.psychometric.CreatePsychometricTestRequest;
import com.edge.pulse.data.dto.psychometric.CreateScaleRequest;
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
import com.edge.pulse.services.psychometric.PsychometricAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Transactional orchestration that turns a validated {@link ParsedPackage} into a scoreable
 * {@code PsychometricTest} by driving the existing {@link PsychometricAdminService} create
 * methods, mapping package-local names to generated UUIDs.
 *
 * <p><strong>Refuse-partial:</strong> the whole method runs in one transaction. Any unresolved
 * reference (unknown scale/question name) throws {@link IllegalArgumentException}, rolling back
 * every row written so far — no half-imported tests.
 *
 * <p>Assumes the {@link ParsedPackage} has already passed
 * {@link AssessmentPackageParser} validation (the controller refuses to call the importer when
 * the parser reported errors).
 */
@Service
public class AssessmentImporter {

    private final PsychometricAdminService admin;

    public AssessmentImporter(PsychometricAdminService admin) {
        this.admin = admin;
    }

    @Transactional
    public ImportResultDto importPackage(ImportPackageRequest meta, ParsedPackage pkg, UUID userId) {
        // 1. Create the test (Form + PsychometricTest).
        PsychometricTestDto test = admin.createTest(new CreatePsychometricTestRequest(
                meta.testName(), meta.description(), null, meta.testType(), meta.timeLimitSecs()), userId);
        UUID testId = test.testId();

        // 2. Questions: header -> questionId, and (header,value) -> candidateAnswerId.
        Map<String, UUID> questionIdByHeader = new LinkedHashMap<>();
        Map<String, Map<Integer, UUID>> optionIdByHeaderValue = new HashMap<>();
        for (ParsedQuestion q : pkg.questions()) {
            QuestionType qType = questionTypeFor(pkg, q);
            AddQuestionRequest req;
            if (qType == QuestionType.SCALE) {
                // A SCALE question is answered via answer_scale.value (1..N) — it must carry
                // scaleMin/scaleMax (DB chk_scale_range) and NO candidate answers. The min/max
                // labels come from the lowest- and highest-value options.
                ParsedOption lo = q.options().stream()
                        .min(java.util.Comparator.comparingInt(ParsedOption::value))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "SCALE question '" + q.header() + "' has no options to derive a range from"));
                ParsedOption hi = q.options().stream()
                        .max(java.util.Comparator.comparingInt(ParsedOption::value))
                        .orElseThrow(() -> new IllegalArgumentException(
                                "SCALE question '" + q.header() + "' has no options to derive a range from"));
                req = new AddQuestionRequest(
                        q.bodyEn(), q.bodyAr(), qType,
                        null, null, 0,
                        null,                       // candidateAnswers — NONE for SCALE
                        null, null,                 // subjectLabels / subjectLabelsAr
                        lo.value(), hi.value(),      // scaleMin / scaleMax
                        lo.labelEn(), lo.labelAr(), // minLabel / minLabelAr
                        hi.labelEn(), hi.labelAr(), // maxLabel / maxLabelAr
                        null);                      // forcedChoicePairs
            } else {
                // TODO(Phase-1D): populate CandidateAnswer.tagScaleId from the scoring sheet's per-option
                // tag for OPTION_TAGGED_TALLY (VIP); options currently import with tag_scale_id=NULL so VIP
                // scores nothing until wired.
                List<CandidateAnswerDto> opts = q.options().stream()
                        .map(o -> new CandidateAnswerDto(null, o.labelEn(), o.labelAr(), o.displayOrder()))
                        .toList();
                req = new AddQuestionRequest(
                        q.bodyEn(), q.bodyAr(), qType,
                        null, null, 0,
                        opts,
                        null, null,           // subjectLabels / subjectLabelsAr
                        null, null,           // scaleMin / scaleMax
                        null, null,           // minLabel / minLabelAr
                        null, null,           // maxLabel / maxLabelAr
                        null);                // forcedChoicePairs
            }
            QuestionDto created = admin.addQuestion(testId, req, userId);
            questionIdByHeader.put(q.header(), created.id());

            // (header,value) -> optionId is only used to resolve ANSWER_KEY_SINGLE correctAnswerId.
            // SCALE/Likert items are answered via answer_scale.value and carry no candidate answers,
            // so skip the map for SCALE (created.candidateAnswers() is null/empty → NPE-safe anyway).
            if (qType != QuestionType.SCALE) {
                // Map created options to parsed values by displayOrder, not list position, so that any
                // reordering in the service layer does not silently wire the wrong option ids.
                List<CandidateAnswerDto> createdOpts = created.candidateAnswers();
                Map<Integer, UUID> byValue = new HashMap<>();
                if (createdOpts != null) {
                    for (CandidateAnswerDto ca : createdOpts) {
                        int order = ca.displayOrder();
                        if (order < 0 || order >= q.options().size()) {
                            throw new IllegalArgumentException(
                                    "Returned option has displayOrder " + order
                                    + " which is out of range [0, " + q.options().size()
                                    + ") for question '" + q.header() + "'");
                        }
                        ParsedOption parsed = q.options().get(order);
                        byValue.put(parsed.value(), ca.id());
                    }
                }
                optionIdByHeaderValue.put(q.header(), byValue);
            }
        }

        // 3. Scales: parents-first so child rows can reference an already-created parent id.
        Map<String, UUID> scaleIdByName = createScalesInOrder(testId, pkg, userId);

        // 4. Scoring-key items: resolve names -> ids; ANSWER_KEY_SINGLE pulls correctAnswerId
        //    from the answer key value -> candidate-answer id.
        Map<String, Integer> correctValueByHeader = pkg.answerKey().stream()
                .collect(Collectors.toMap(AnswerKeyEntry::questionHeader, AnswerKeyEntry::correctValue,
                        (a, b) -> a));
        List<ScoringKeyItemRequest> keyItems = new ArrayList<>();
        for (ScoringSheetItem it : pkg.items()) {
            UUID questionId = questionIdByHeader.get(it.questionHeader());
            if (questionId == null) {
                throw new IllegalArgumentException(
                        "Scoring item references unknown question: " + it.questionHeader());
            }
            UUID scaleId = scaleIdByName.get(it.scaleName());
            if (scaleId == null) {
                throw new IllegalArgumentException(
                        "Scoring item references unknown scale: " + it.scaleName());
            }

            UUID correctAnswerId = null;
            if (it.itemStrategy() == ItemStrategyType.ANSWER_KEY_SINGLE
                    && correctValueByHeader.containsKey(it.questionHeader())) {
                Map<Integer, UUID> byValue = optionIdByHeaderValue.get(it.questionHeader());
                int correctValue = correctValueByHeader.get(it.questionHeader());
                correctAnswerId = byValue != null ? byValue.get(correctValue) : null;
                if (correctAnswerId == null) {
                    throw new IllegalArgumentException("Answer key value " + correctValue
                            + " has no matching option for question " + it.questionHeader());
                }
            }

            keyItems.add(new ScoringKeyItemRequest(
                    questionId,
                    scaleId,
                    it.direction() != null ? it.direction().name() : null,
                    BigDecimal.valueOf(it.weight()),
                    correctAnswerId,
                    false,
                    it.itemStrategy()));
        }
        admin.saveScoringKey(testId, keyItems, userId);

        // 5. Parametric norms: PARAMETRIC scales with a mean -> NormScaleParam rows.
        List<NormScaleParamRequest> norms = new ArrayList<>();
        for (ScoringSheetScale s : pkg.scales()) {
            if (s.normStrategy() == NormStrategyType.PARAMETRIC && s.mean() != null) {
                UUID scaleId = scaleIdByName.get(s.name());
                if (scaleId == null) {
                    throw new IllegalArgumentException("Norm references unknown scale: " + s.name());
                }
                norms.add(new NormScaleParamRequest(scaleId, s.mean(), s.sd(),
                        s.tFactor(), s.tOffset(), s.tClipLo(), s.tClipHi(), null));
            }
        }
        if (!norms.isEmpty()) {
            admin.saveParametricNorms(testId, norms, userId);
        }

        return new ImportResultDto(true, testId,
                pkg.questions().size(), pkg.scales().size(), pkg.items().size(),
                norms.size(), List.of());
    }

    /**
     * Creates scales parents-first: a scale is created once its {@code parentName} (if any) has
     * already been created. Detects unresolved/cyclic parents by failing if a full pass makes
     * no progress.
     */
    private Map<String, UUID> createScalesInOrder(UUID testId, ParsedPackage pkg, UUID userId) {
        Map<String, UUID> scaleIdByName = new LinkedHashMap<>();
        Map<String, ScoringSheetScale> byName = pkg.scales().stream()
                .collect(Collectors.toMap(ScoringSheetScale::name, s -> s, (a, b) -> a,
                        LinkedHashMap::new));

        List<ScoringSheetScale> remaining = new ArrayList<>(byName.values());
        while (!remaining.isEmpty()) {
            List<ScoringSheetScale> next = new ArrayList<>();
            boolean progressed = false;
            for (ScoringSheetScale s : remaining) {
                String parentName = s.parentName();
                boolean hasParent = parentName != null && !parentName.isBlank();
                if (hasParent && !byName.containsKey(parentName)) {
                    throw new IllegalArgumentException(
                            "Scale '" + s.name() + "' references unknown parent: " + parentName);
                }
                UUID parentId = hasParent ? scaleIdByName.get(parentName) : null;
                if (hasParent && parentId == null) {
                    next.add(s);   // parent not created yet — defer
                    continue;
                }
                CreateScaleRequest req = new CreateScaleRequest(
                        s.name(),
                        null,                                          // description
                        s.scoreMethod() != null ? s.scoreMethod().name() : null,
                        parentId,
                        0,                                             // displayOrder
                        s.compositeMethod(),
                        s.compositeBasis(),
                        s.roundingScale());
                PsychometricScaleDto created = admin.createScale(testId, req, userId);
                scaleIdByName.put(s.name(), created.scaleId());
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalArgumentException(
                        "Unresolvable scale parent hierarchy (cycle or missing parent)");
            }
            remaining = next;
        }
        return scaleIdByName;
    }

    /**
     * Determines the {@link QuestionType} for a question from the item strategies of all scoring
     * items that reference its header. Multiple items sharing the SAME strategy (e.g. VIP's
     * per-scale OPTION_TAGGED_TALLY items) are fine. If two distinct strategies that map to
     * different QuestionTypes both reference the same question header an
     * {@link IllegalArgumentException} is thrown — such a package is malformed.
     *
     * <p>When NO scoring item references the question (e.g. PTI validity/consistency items that
     * are not scored on any substantive scale), the type is INFERRED from the option structure:
     * a contiguous {@code 1..N} integer rating scale of ≥3 options → {@link QuestionType#SCALE};
     * exactly 2 options → {@link QuestionType#FORCED_CHOICE}; otherwise
     * {@link QuestionType#CHOICE_SINGLE}. This keeps unmapped Likert validity items valid for a
     * PERSONALITY test (which forbids CHOICE_SINGLE).
     */
    private QuestionType questionTypeFor(ParsedPackage pkg, ParsedQuestion q) {
        String header = q.header();
        Set<QuestionType> distinctTypes = pkg.items().stream()
                .filter(it -> header.equals(it.questionHeader()))
                .map(ScoringSheetItem::itemStrategy)
                .filter(s -> s != null)
                .map(s -> switch (s) {
                    case LIKERT_VALUE -> QuestionType.SCALE;
                    case ANSWER_KEY_MULTIPLE -> QuestionType.CHOICE_MULTIPLE;
                    case ADJECTIVE_COUNT -> QuestionType.ADJECTIVE_CHECKLIST;
                    case ANSWER_KEY_SINGLE, BINARY_FORCED_CHOICE, OPTION_TAGGED_TALLY -> QuestionType.CHOICE_SINGLE;
                })
                .collect(Collectors.toSet());

        if (distinctTypes.size() > 1) {
            throw new IllegalArgumentException(
                    "Conflicting question types for question '" + header + "': " + distinctTypes);
        }
        if (!distinctTypes.isEmpty()) {
            return distinctTypes.iterator().next();
        }
        // Unmapped question — infer from option structure.
        return inferTypeFromOptions(q.options());
    }

    /**
     * Infers a {@link QuestionType} for an unmapped question from its option {@code value}s:
     * a contiguous {@code 1..N} integer rating scale of ≥3 options → SCALE; exactly 2 options →
     * FORCED_CHOICE; otherwise CHOICE_SINGLE.
     */
    private QuestionType inferTypeFromOptions(List<ParsedOption> options) {
        int n = options.size();
        if (n >= 3) {
            Set<Integer> values = options.stream().map(ParsedOption::value).collect(Collectors.toSet());
            boolean contiguousFromOne = values.size() == n;
            if (contiguousFromOne) {
                for (int v = 1; v <= n; v++) {
                    if (!values.contains(v)) {
                        contiguousFromOne = false;
                        break;
                    }
                }
            }
            if (contiguousFromOne) {
                return QuestionType.SCALE;
            }
        } else if (n == 2) {
            return QuestionType.FORCED_CHOICE;
        }
        return QuestionType.CHOICE_SINGLE;
    }
}
