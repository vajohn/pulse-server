package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.enums.ScoreMethod;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.models.AnswerAdjective;
import com.edge.pulse.data.models.AnswerChoice;
import com.edge.pulse.data.models.AnswerScale;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.psychometric.*;
import com.edge.pulse.repositories.answer.AnswerAdjectiveRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.psychometric.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scoring engine for psychometric tests.
 *
 * <p>Orchestration:
 * <ol>
 *   <li>Detects whether the completed session belongs to a {@link PsychometricTest}.</li>
 *   <li>Loads the single ACTIVE {@link ScoringKeyVersion} for the test.</li>
 *   <li>Computes leaf-scale raw scores using the appropriate algorithm:
 *       <ul>
 *         <li>CHOICE items (cognitive): 1 × weight if selected answer is keyed correct; otherwise 0.</li>
 *         <li>SCALE items (personality): applies {@link ScoreDirection} (FORWARD/REVERSE), then × weight.</li>
 *       </ul>
 *   </li>
 *   <li>Rolls up leaf scores to parent scales (SUM or MEAN).</li>
 *   <li>Looks up sten / percentile / z-score from the active {@link NormTableVersion}.</li>
 *   <li>Persists {@link TestResult} + one {@link ScaleScore} per scale.</li>
 * </ol>
 *
 * <p>If no ACTIVE scoring key exists the result is persisted with status PENDING
 * and no scale scores — it will be re-scored when a key is published.
 * If no VALIDATED norm table exists, scale scores are persisted with null sten/percentile.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScoringService {

    private final PsychometricTestRepository testRepository;
    private final ScoringKeyVersionRepository scoringKeyVersionRepository;
    private final ScoringKeyItemRepository scoringKeyItemRepository;
    private final NormTableVersionRepository normTableVersionRepository;
    private final NormEntryRepository normEntryRepository;
    private final NormScaleParamRepository normScaleParamRepository;
    private final TestResultRepository testResultRepository;
    private final ScaleScoreRepository scaleScoreRepository;
    private final PsychometricScaleRepository scaleRepository;
    private final AnswerScaleRepository answerScaleRepository;
    private final AnswerChoiceRepository answerChoiceRepository;
    private final AnswerAdjectiveRepository answerAdjectiveRepository;
    private final ScoringKeyCorrectAnswerRepository scoringKeyCorrectAnswerRepository;
    private final CompetencyScaleWeightRepository competencyScaleWeightRepository;
    private final CompetencyScoreRepository competencyScoreRepository;

    /**
     * Entry point called from {@link com.edge.pulse.services.SessionService} after
     * a session is marked complete.
     *
     * <p>If the session's survey has no associated {@link PsychometricTest} this method
     * returns immediately with no side effects.
     */
    @Transactional
    public void scoreSession(ResponseSession session) {
        UUID formId = session.getForm().getId();

        Optional<PsychometricTest> testOpt = testRepository.findByFormId(formId);
        if (testOpt.isEmpty()) {
            return; // Not a psychometric test — nothing to score
        }
        PsychometricTest test = testOpt.get();

        // Guard: re-scoring an already-scored session is not allowed here
        if (testResultRepository.findBySessionId(session.getId()).isPresent()) {
            log.warn("ScoringService: session {} already has a TestResult — skipping", session.getId());
            return;
        }

        Optional<ScoringKeyVersion> keyOpt = scoringKeyVersionRepository
                .findFirstByTestIdAndStatus(test.getId(), ScoringKeyStatus.ACTIVE);

        if (keyOpt.isEmpty()) {
            log.info("ScoringService: no ACTIVE scoring key for test {} — persisting PENDING result", test.getId());
            persistPendingResult(test, session);
            return;
        }

        ScoringKeyVersion key = keyOpt.get();
        List<ScoringKeyItem> items = scoringKeyItemRepository.findByScoringKeyIdWithDetails(key.getId());

        // ── Batch-load all answer types for this session — avoids N+1 ────────
        // CHOICE answers: keyed by questionId → list (supports CHOICE_MULTIPLE)
        Map<UUID, List<AnswerChoice>> choicesByQuestion = answerChoiceRepository
                .findCurrentBySessionId(session.getId())
                .stream()
                .collect(Collectors.groupingBy(ac -> ac.getSubmission().getQuestion().getId()));

        Map<UUID, AnswerScale> scaleByQuestion = answerScaleRepository
                .findCurrentBySessionId(session.getId())
                .stream()
                .collect(Collectors.toMap(ans -> ans.getSubmission().getQuestion().getId(), ans -> ans));

        Map<UUID, AnswerAdjective> adjectiveByQuestion = answerAdjectiveRepository
                .findCurrentBySessionId(session.getId())
                .stream()
                .collect(Collectors.toMap(aa -> aa.getSubmission().getQuestion().getId(), aa -> aa));

        // Batch-load the CHOICE_MULTIPLE correct-answer sets for items that need them
        List<UUID> multipleChoiceItemIds = items.stream()
                .filter(i -> i.getQuestion().getQuestionType() == QuestionType.CHOICE_MULTIPLE)
                .map(ScoringKeyItem::getId)
                .toList();
        Map<UUID, List<UUID>> correctAnswerIdsByItem = multipleChoiceItemIds.isEmpty()
                ? Map.of()
                : scoringKeyCorrectAnswerRepository.findByItemIdIn(multipleChoiceItemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                ca -> ca.getScoringKeyItem().getId(),
                                Collectors.mapping(
                                        ca -> ca.getCandidateAnswer().getId(),
                                        Collectors.toList())));

        // ── Leaf scale raw scores ────────────────────────────────────────────────
        // scaleRawAccumulator: scaleId → [weightedSum, weightSum, itemsAnswered, itemsTotal]
        Map<UUID, double[]> acc = new LinkedHashMap<>();
        Map<UUID, PsychometricScale> scalesById = new HashMap<>();

        for (ScoringKeyItem item : items) {
            UUID qId = item.getQuestion().getId();
            UUID scaleId = item.getScale().getId();
            scalesById.put(scaleId, item.getScale());

            double[] bucket = acc.computeIfAbsent(scaleId, k -> new double[]{0, 0, 0, 0});
            double weight = item.getWeight().doubleValue();
            bucket[3] += 1; // itemsTotal

            double rawValue = computeItemScore(item,
                    choicesByQuestion.getOrDefault(qId, List.of()),
                    scaleByQuestion.get(qId),
                    adjectiveByQuestion.get(qId),
                    correctAnswerIdsByItem.getOrDefault(item.getId(), List.of()));
            if (!Double.isNaN(rawValue)) {
                bucket[0] += rawValue * weight; // weighted sum
                bucket[1] += weight;            // weight sum (for MEAN)
                bucket[2] += 1;                 // itemsAnswered
            }
        }

        // ── Compute final leaf raw scores ────────────────────────────────────────
        Map<UUID, BigDecimal> leafRawScores = new HashMap<>();
        for (Map.Entry<UUID, double[]> entry : acc.entrySet()) {
            UUID scaleId = entry.getKey();
            double[] b = entry.getValue();
            PsychometricScale scale = scalesById.get(scaleId);
            double raw = computeScaleRaw(scale, b);
            leafRawScores.put(scaleId, BigDecimal.valueOf(raw).setScale(3, RoundingMode.HALF_UP));
        }

        // ── Parent scale rollup ──────────────────────────────────────────────────
        List<PsychometricScale> allScales = scaleRepository.findByTestId(test.getId());
        Map<UUID, BigDecimal> allRawScores = new HashMap<>(leafRawScores);
        rollupParentScores(allScales, leafRawScores, allRawScores);

        // ── Norm lookup ──────────────────────────────────────────────────────────
        Optional<NormTableVersion> normOpt = normTableVersionRepository
                .findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED);

        // ── Persist TestResult ───────────────────────────────────────────────────
        TestResult result = TestResult.builder()
                .test(test)
                .session(session)
                .scoringKeyVersion(key)
                .normTableVersion(normOpt.orElse(null))
                .status(TestResultStatus.SCORED)
                .scoredAt(LocalDateTime.now())
                .focusLossCount(session.getFocusLossCount())
                .build();
        result = testResultRepository.save(result);

        // ── Persist ScaleScores ──────────────────────────────────────────────────
        List<ScaleScore> savedScaleScores = new ArrayList<>();
        for (PsychometricScale scale : allScales) {
            BigDecimal rawScore = allRawScores.get(scale.getId());
            if (rawScore == null) {
                continue; // parent scale with no contributing items — skip
            }

            double[] bucket = acc.getOrDefault(scale.getId(), null);
            int itemsAnswered = bucket != null ? (int) bucket[2] : 0;
            int itemsTotal = bucket != null ? (int) bucket[3] : 0;
            // Parent scales don't have their own item buckets — use 0/0 to signal rollup
            if (scale.getParentScale() == null && !acc.containsKey(scale.getId())) {
                itemsAnswered = 0;
                itemsTotal = 0;
            }

            BigDecimal stenScore = null;
            BigDecimal percentile = null;
            BigDecimal zScore = null;

            if (normOpt.isPresent()) {
                // Parametric-normal norm: standardise the raw score against the scale's (mean, sd).
                var paramOpt = normScaleParamRepository
                        .findByNormTable_IdAndScale_Id(normOpt.get().getId(), scale.getId());
                if (paramOpt.isPresent()) {
                    NormScaleParam p = paramOpt.get();
                    zScore = NormStandardizer.zScore(rawScore, p.getMean(), p.getSd());
                    stenScore = BigDecimal.valueOf(NormStandardizer.sten(zScore));
                    percentile = NormStandardizer.percentile(zScore);
                }
            }

            ScaleScore ss = ScaleScore.builder()
                    .result(result)
                    .scale(scale)
                    .rawScore(rawScore)
                    .stenScore(stenScore)
                    .percentile(percentile)
                    .zScore(zScore)
                    .itemsAnswered(itemsAnswered)
                    .itemsTotal(itemsTotal)
                    .build();
            savedScaleScores.add(scaleScoreRepository.save(ss));
        }

        // ── Competency scoring ───────────────────────────────────────────────────
        scoreCompetencies(result, savedScaleScores);

        log.info("ScoringService: scored session {} for test {} — {} scale scores",
                session.getId(), test.getId(), allScales.size());
    }

    /**
     * Re-scores an existing PENDING result after a scoring key has been published.
     * Called by the admin re-score endpoint.
     */
    @Transactional
    public void rescoreResult(UUID resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("TestResult not found: " + resultId));
        // Delete old scores (competency scores cascade delete from test_result ON DELETE CASCADE,
        // but we also delete manually to keep the transaction explicit)
        competencyScoreRepository.deleteByResultId(result.getId());
        scaleScoreRepository.deleteByResultId(result.getId());
        testResultRepository.delete(result);
        scoreSession(result.getSession());
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private void persistPendingResult(PsychometricTest test, ResponseSession session) {
        TestResult result = TestResult.builder()
                .test(test)
                .session(session)
                .status(TestResultStatus.PENDING)
                .focusLossCount(session.getFocusLossCount())
                .build();
        testResultRepository.save(result);
    }

    /**
     * Computes the raw item score for a single scoring key item supporting all
     * four psychometric question types.
     *
     * <ul>
     *   <li><b>CHOICE_SINGLE / FORCED_CHOICE</b> — 1.0 if the selected answer matches
     *       {@code correctAnswer}; otherwise 0.0.</li>
     *   <li><b>CHOICE_MULTIPLE</b> — full or partial credit depending on
     *       {@link ScoringKeyItem#isPartialCredit()}.</li>
     *   <li><b>ADJECTIVE_CHECKLIST</b> — count of selected adjectives (raw count
     *       before weight application).</li>
     *   <li><b>SCALE / PERSONALITY</b> — scale value with optional REVERSE direction.</li>
     * </ul>
     *
     * @return the weighted-ready item value, or {@code Double.NaN} if the item was not answered
     */
    private double computeItemScore(ScoringKeyItem item,
                                    List<AnswerChoice> choiceAnswers,
                                    AnswerScale scaleAnswer,
                                    AnswerAdjective adjectiveAnswer,
                                    List<UUID> correctAnswerIds) {
        QuestionType qType = item.getQuestion().getQuestionType();

        return switch (qType) {
            case CHOICE_SINGLE, FORCED_CHOICE -> {
                // Single correct answer keyed by ScoringKeyItem.correctAnswer
                if (choiceAnswers.isEmpty()) yield Double.NaN;
                if (item.getCorrectAnswer() == null) yield Double.NaN;
                boolean correct = item.getCorrectAnswer().getId()
                        .equals(choiceAnswers.get(0).getCandidateAnswer().getId());
                yield correct ? 1.0 : 0.0;
            }
            case CHOICE_MULTIPLE -> {
                if (choiceAnswers.isEmpty()) yield Double.NaN;
                if (correctAnswerIds.isEmpty()) yield 0.0; // no key defined → 0
                Set<UUID> selectedIds = choiceAnswers.stream()
                        .map(ac -> ac.getCandidateAnswer().getId())
                        .collect(Collectors.toSet());
                Set<UUID> keySet = new HashSet<>(correctAnswerIds);
                if (!item.isPartialCredit()) {
                    // All-or-nothing: exact match required
                    yield selectedIds.equals(keySet) ? 1.0 : 0.0;
                } else {
                    // Partial credit: +1 per correct, -0.25 per incorrect, ≥ 0
                    long correctCount = selectedIds.stream().filter(keySet::contains).count();
                    long incorrectCount = selectedIds.stream().filter(id -> !keySet.contains(id)).count();
                    yield Math.max(0.0, correctCount - 0.25 * incorrectCount);
                }
            }
            case ADJECTIVE_CHECKLIST -> {
                if (adjectiveAnswer == null) yield Double.NaN;
                // Score = count of selected adjectives (weight applied by caller)
                yield (double) adjectiveAnswer.getSelected().size();
            }
            default -> {
                // SCALE / PERSONALITY
                if (scaleAnswer == null) yield Double.NaN;
                int value = scaleAnswer.getValue();
                if (item.getDirection() == ScoreDirection.REVERSE) {
                    value = scaleAnswer.getMaxValue() + scaleAnswer.getMinValue() - value;
                }
                yield (double) value;
            }
        };
    }

    /**
     * Converts the accumulated bucket into a final raw score per the scale's scoring method.
     *
     * <p>bucket: [0]=weightedSum, [1]=weightSum, [2]=itemsAnswered, [3]=itemsTotal
     */
    private double computeScaleRaw(PsychometricScale scale, double[] bucket) {
        if (bucket[2] == 0) return 0.0; // no items answered → 0
        if (scale.getScoreMethod() == ScoreMethod.MEAN && bucket[1] > 0) {
            return bucket[0] / bucket[1]; // weighted mean
        }
        return bucket[0]; // weighted sum (default)
    }

    /**
     * Computes weighted competency scores from the scale sten scores just persisted.
     *
     * <p>Each competency aggregates the sten scores of its contributing scales weighted
     * by {@link CompetencyScaleWeight}. REVERSE direction inverts the sten (11 - sten).
     * The final score is clamped to [0, 10] and stored with 3 decimal places.
     */
    private void scoreCompetencies(TestResult result, List<ScaleScore> scaleScores) {
        Map<UUID, BigDecimal> stenByScale = scaleScores.stream()
                .filter(ss -> ss.getStenScore() != null)
                .collect(Collectors.toMap(ss -> ss.getScale().getId(), ScaleScore::getStenScore));
        if (stenByScale.isEmpty()) return;

        List<CompetencyScaleWeight> weights =
                competencyScaleWeightRepository.findByScaleIdIn(stenByScale.keySet());
        if (weights.isEmpty()) return;

        weights.stream()
                .collect(Collectors.groupingBy(CompetencyScaleWeight::getCompetency))
                .forEach((competency, cWeights) -> {
                    double totalWeight = 0.0, weightedSum = 0.0;
                    for (CompetencyScaleWeight w : cWeights) {
                        BigDecimal sten = stenByScale.get(w.getScale().getId());
                        if (sten == null) continue;
                        double raw = w.getDirection() == ScoreDirection.REVERSE
                                ? (11.0 - sten.doubleValue()) : sten.doubleValue();
                        weightedSum += raw * w.getWeight().doubleValue();
                        totalWeight += w.getWeight().doubleValue();
                    }
                    if (totalWeight == 0.0) return;
                    double normalized = Math.min(10.0, Math.max(0.0, weightedSum / totalWeight));
                    competencyScoreRepository.save(CompetencyScore.builder()
                            .result(result)
                            .competency(competency)
                            .score(BigDecimal.valueOf(normalized).setScale(3, RoundingMode.HALF_UP))
                            .build());
                });
    }

    /**
     * Rolls up leaf raw scores into parent scales using a topological ordering so that
     * scales at greater depth (closer to root) are always computed after all of their
     * children — correctly handling hierarchies of arbitrary depth.
     *
     * <p>Algorithm: Kahn-style BFS. Start with parents whose entire child set is already
     * in {@code computed}. Each promoted parent is then available as a child for its own
     * parent in the next iteration.
     */
    private void rollupParentScores(List<PsychometricScale> allScales,
                                    Map<UUID, BigDecimal> leafRawScores,
                                    Map<UUID, BigDecimal> allRawScores) {
        // Build parent → direct-children ID list
        Map<UUID, List<UUID>> parentToChildren = new HashMap<>();
        for (PsychometricScale s : allScales) {
            if (s.getParentScale() != null) {
                parentToChildren
                        .computeIfAbsent(s.getParentScale().getId(), k -> new ArrayList<>())
                        .add(s.getId());
            }
        }

        // IDs of all scales that already have a score (leaf scores computed above)
        Set<UUID> computed = new HashSet<>(leafRawScores.keySet());

        // Remaining parent IDs that still need rollup — use LinkedHashSet for determinism
        Set<UUID> remaining = new LinkedHashSet<>(parentToChildren.keySet());

        while (!remaining.isEmpty()) {
            boolean progress = false;
            Iterator<UUID> it = remaining.iterator();
            while (it.hasNext()) {
                UUID parentId = it.next();
                List<UUID> children = parentToChildren.getOrDefault(parentId, List.of());
                if (computed.containsAll(children)) {
                    // All children have scores — compute this parent now
                    BigDecimal parentSum = children.stream()
                            .map(cId -> allRawScores.getOrDefault(cId, BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    allRawScores.put(parentId, parentSum.setScale(3, RoundingMode.HALF_UP));
                    computed.add(parentId);
                    it.remove();
                    progress = true;
                }
            }
            if (!progress) {
                // Guard against a cycle or disconnected parent — should not occur with valid data
                log.warn("ScoringService: rollupParentScores could not resolve all parent scores; remaining={}", remaining);
                break;
            }
        }
    }
}
