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
            QuestionType qType = questionTypeFor(pkg, q.header());
            List<CandidateAnswerDto> opts = q.options().stream()
                    .map(o -> new CandidateAnswerDto(null, o.labelEn(), o.labelAr(), o.displayOrder()))
                    .toList();
            AddQuestionRequest req = new AddQuestionRequest(
                    q.bodyEn(), q.bodyAr(), qType,
                    null, null, 0,
                    opts,
                    null, null,           // subjectLabels / subjectLabelsAr
                    null, null,           // scaleMin / scaleMax
                    null, null,           // minLabel / minLabelAr
                    null, null,           // maxLabel / maxLabelAr
                    null);                // forcedChoicePairs
            QuestionDto created = admin.addQuestion(testId, req, userId);
            questionIdByHeader.put(q.header(), created.id());

            // The created options come back in the order we sent them (parsed displayOrder order),
            // so option i corresponds to parsed value i.
            List<CandidateAnswerDto> createdOpts = created.candidateAnswers();
            Map<Integer, UUID> byValue = new HashMap<>();
            if (createdOpts != null) {
                for (int i = 0; i < createdOpts.size() && i < q.options().size(); i++) {
                    int value = q.options().get(i).value();
                    byValue.put(value, createdOpts.get(i).id());
                }
            }
            optionIdByHeaderValue.put(q.header(), byValue);
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
     * Determines the {@link QuestionType} for a question header from the item strategy of the
     * scoring item that references it. Falls back to CHOICE_SINGLE when no item references the
     * header (defensive; the parser guarantees every item's header exists).
     */
    private QuestionType questionTypeFor(ParsedPackage pkg, String header) {
        ItemStrategyType strategy = pkg.items().stream()
                .filter(it -> header.equals(it.questionHeader()))
                .map(ScoringSheetItem::itemStrategy)
                .findFirst()
                .orElse(null);
        if (strategy == null) {
            return QuestionType.CHOICE_SINGLE;
        }
        return switch (strategy) {
            case LIKERT_VALUE -> QuestionType.SCALE;
            case ANSWER_KEY_MULTIPLE -> QuestionType.CHOICE_MULTIPLE;
            case ADJECTIVE_COUNT -> QuestionType.ADJECTIVE_CHECKLIST;
            case ANSWER_KEY_SINGLE, BINARY_FORCED_CHOICE, OPTION_TAGGED_TALLY -> QuestionType.CHOICE_SINGLE;
        };
    }
}
