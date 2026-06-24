package com.edge.pulse.services.psychometric;

import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.NormStatus;
import com.edge.pulse.data.enums.NormStrategyType;
import com.edge.pulse.data.enums.QuestionType;
import com.edge.pulse.data.enums.ResultMode;
import com.edge.pulse.data.enums.ResultState;
import com.edge.pulse.data.enums.ScaleProgressState;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.data.enums.ScoringKeyStatus;
import com.edge.pulse.data.enums.TestResultStatus;
import com.edge.pulse.data.enums.ValidityStatus;
import com.edge.pulse.data.models.AnswerAdjective;
import com.edge.pulse.data.models.AnswerChoice;
import com.edge.pulse.data.models.AnswerScale;
import com.edge.pulse.data.models.CandidateAnswer;
import com.edge.pulse.data.models.ResponseSession;
import com.edge.pulse.data.models.psychometric.*;
import com.edge.pulse.repositories.answer.AnswerAdjectiveRepository;
import com.edge.pulse.repositories.answer.AnswerChoiceRepository;
import com.edge.pulse.repositories.answer.AnswerScaleRepository;
import com.edge.pulse.repositories.psychometric.*;
import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import com.edge.pulse.services.psychometric.scoring.model.*;
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
 * <p>This class is the JPA/transaction boundary: it loads the scoring-key + norm
 * configuration and the candidate's answers, flattens them into the pure
 * {@link ScoringInput}, delegates the maths to {@link ScoringCalculator}, then
 * persists the {@link TestResult} + per-scale {@link ScaleScore} + competency scores.
 *
 * <p>Orchestration:
 * <ol>
 *   <li>Detects whether the completed session belongs to a {@link PsychometricTest}.</li>
 *   <li>Loads the single ACTIVE {@link ScoringKeyVersion} for the test.</li>
 *   <li>Maps each {@link ScoringKeyItem} → {@link ItemConfig} (strategy resolved from
 *       {@code item_strategy}, falling back to the question type) and each answered
 *       question → {@link ItemResponse}.</li>
 *   <li>Maps each {@link PsychometricScale} → {@link ScaleConfig}, attaching a
 *       {@link NormConfig} from the active {@link NormTableVersion} (PARAMETRIC params
 *       or EMPIRICAL_PERCENTILE buckets).</li>
 *   <li>Calls {@link ScoringCalculator#calculate(ScoringInput)} and persists the result.</li>
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
    private final UserItemExposureRepository userItemExposureRepository;
    private final ScaleProgressRepository scaleProgressRepository;

    /**
     * Entry point called from {@link com.edge.pulse.services.SessionService} after
     * a session is marked complete.
     *
     * <p>If the session's form has no associated {@link PsychometricTest} this method
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

        // Batch-load the multi-select correct-answer sets for ANSWER_KEY_MULTIPLE items
        List<UUID> multipleChoiceItemIds = items.stream()
                .filter(i -> resolveStrategy(i) == ItemStrategyType.ANSWER_KEY_MULTIPLE)
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

        // ── Build ItemConfigs + ItemResponses ────────────────────────────────────
        List<ItemConfig> itemConfigs = new ArrayList<>(items.size());
        Map<UUID, ItemResponse> responsesByQuestion = new HashMap<>();

        for (ScoringKeyItem item : items) {
            UUID qId = item.getQuestion().getId();
            ItemStrategyType strategy = resolveStrategy(item);
            itemConfigs.add(new ItemConfig(
                    qId,
                    item.getScale().getId(),
                    strategy,
                    item.getDirection(),
                    item.getWeight().doubleValue(),
                    item.getCorrectAnswer() != null ? item.getCorrectAnswer().getId() : null,
                    correctAnswerIdsByItem.getOrDefault(item.getId(), List.of()),
                    item.isPartialCredit(),
                    null /* tagScaleId: resolved per-response for OPTION_TAGGED_TALLY */));

            // Build the ItemResponse for this question (only once per question)
            if (!responsesByQuestion.containsKey(qId)) {
                ItemResponse r = buildResponse(strategy, qId,
                        scaleByQuestion.get(qId),
                        choicesByQuestion.getOrDefault(qId, List.of()),
                        adjectiveByQuestion.get(qId));
                if (r != null) {
                    responsesByQuestion.put(qId, r);
                }
            }
        }

        // ── Build ScaleConfigs (with parent→children map + norm) ──────────────────
        List<PsychometricScale> allScales = scaleRepository.findByTestId(test.getId());
        Map<UUID, PsychometricScale> scaleById = allScales.stream()
                .collect(Collectors.toMap(PsychometricScale::getId, s -> s));

        Optional<NormTableVersion> normOpt = normTableVersionRepository
                .findFirstByTestIdAndStatus(test.getId(), NormStatus.VALIDATED);

        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (PsychometricScale s : allScales) {
            if (s.getParentScale() != null) {
                childrenByParent
                        .computeIfAbsent(s.getParentScale().getId(), k -> new ArrayList<>())
                        .add(s.getId());
            }
        }

        // ── Micro-engagement accrual (Phase 3) ─────────────────────────────────────
        // Record item exposure for every answered question and advance per-scale accrual
        // progress for CONSOLIDATED scales. Returns the live progress rows keyed by scaleId.
        Map<UUID, ScaleProgress> progressByScale =
                accrueExposureAndProgress(session, test, items, responsesByQuestion, normOpt, allScales);

        List<ScaleConfig> scaleConfigs = new ArrayList<>(allScales.size());
        for (PsychometricScale s : allScales) {
            // ── Consolidation gating (Phase 3, D3/§6.1) ───────────────────────────
            // CONSOLIDATED scales release only once their full required item set has accrued;
            // a below-predicate CONSOLIDATED scale is skipped entirely (never a partial STEN).
            if (s.getResultMode() == ResultMode.CONSOLIDATED) {
                ScaleProgress p = progressByScale.get(s.getId());
                boolean releasable = p != null && p.getItemsCollected() >= p.getItemsRequired();
                if (!releasable) {
                    continue;
                }
                if (p.getState() != ScaleProgressState.CONSOLIDATED) {
                    p.setState(ScaleProgressState.CONSOLIDATED);
                    p.setConsolidatedAt(LocalDateTime.now());
                    scaleProgressRepository.save(p);
                }
            }

            CompositeMethod cm = s.getCompositeMethod();
            List<UUID> children = childrenByParent.getOrDefault(s.getId(), List.of());

            // ── Composite consistency guard ──────────────────────────────────────
            // Only skip a *declared* AGGREGATE_OF_CHILDREN_* composite that is internally
            // inconsistent because it has no child scales to aggregate. Everything else is
            // legitimate and must be built into a ScaleConfig:
            //   • AGGREGATE_OF_ITEMS parents derive their raw from their children's raw via
            //     the children's parentScale FK (ScoringCalculator#rollupRawComposites), so
            //     having FK children is expected — include them with their own norm.
            //   • leaf scales (null composite method) are scored from their own items; a leaf
            //     that other scales point to as parent is still just a leaf — include it.
            boolean aggregatesChildren = cm == CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN
                    || cm == CompositeMethod.AGGREGATE_OF_CHILDREN_SUM;
            if (aggregatesChildren && children.isEmpty()) {
                log.warn("ScoringService: scale {} ({}) has composite method {} but no child scales — skipping",
                        s.getId(), s.getName(), cm);
                continue;
            }

            // AGGREGATE_OF_CHILDREN_* aggregate standardized children (passed explicitly);
            // AGGREGATE_OF_ITEMS parents rebuild their children from the parentScale FK inside
            // the calculator, so they carry no explicit childScaleIds.
            List<UUID> childScaleIds = aggregatesChildren ? children : List.of();
            // CONSOLIDATED scales norm against the version FROZEN for their window (D4),
            // never the live VALIDATED one — a mid-accrual re-norming must not switch the norm.
            NormTableVersion normForScale = normOpt.orElse(null);
            if (s.getResultMode() == ResultMode.CONSOLIDATED) {
                ScaleProgress p = progressByScale.get(s.getId());
                if (p != null && p.getNormTableVersionId() != null) {
                    normForScale = normTableVersionRepository.findById(p.getNormTableVersionId())
                            .orElse(normForScale);
                } else if (p != null) {
                    normForScale = null; // no norm was pinned at window open
                }
            }
            NormConfig norm = buildNormConfig(normForScale, s.getId());

            scaleConfigs.add(new ScaleConfig(
                    s.getId(),
                    s.getName(),
                    s.getParentScale() != null ? s.getParentScale().getId() : null,
                    s.getScoreMethod(),
                    cm,
                    s.getCompositeBasis(),
                    childScaleIds,
                    norm,
                    s.getCompositeRoundingScale()));
        }

        // ── Delegate to the pure calculator ───────────────────────────────────────
        ScoringOutput output = new ScoringCalculator()
                .calculate(new ScoringInput(scaleConfigs, itemConfigs, responsesByQuestion));

        // ── Result state (Phase 3, D3) ─────────────────────────────────────────────
        // PROVISIONAL while any CONSOLIDATED scale is still accruing; FINAL once every
        // required scale has released. IMMEDIATE-only tests have no COLLECTING progress
        // rows → FINAL (zero behaviour change for Phase-1/2 tests).
        boolean anyStillCollecting = progressByScale.values().stream()
                .anyMatch(p -> p.getState() == ScaleProgressState.COLLECTING);
        ResultState resultState = anyStillCollecting ? ResultState.PROVISIONAL : ResultState.FINAL;

        // ── Persist TestResult ───────────────────────────────────────────────────
        TestResult result = TestResult.builder()
                .test(test)
                .session(session)
                .scoringKeyVersion(key)
                .normTableVersion(normOpt.orElse(null))
                .status(TestResultStatus.SCORED)
                .resultState(resultState)
                .scoredAt(LocalDateTime.now())
                .focusLossCount(session.getFocusLossCount())
                .build();
        result = testResultRepository.save(result);

        // ── Persist ScaleScores ──────────────────────────────────────────────────
        List<ScaleScore> savedScaleScores = new ArrayList<>();
        for (ScaleScoreResult r : output.scaleScores()) {
            PsychometricScale scale = scaleById.get(r.scaleId());
            if (scale == null) {
                continue; // defensive — calculator only emits ids it was given
            }
            ScaleScore ss = ScaleScore.builder()
                    .result(result)
                    .scale(scale)
                    .rawScore(r.rawScore())
                    .zScore(r.zScore())
                    .stenScore(r.stenScore())
                    .tScore(r.tScore())
                    .percentile(r.percentile())
                    .itemsAnswered(r.itemsAnswered())
                    .itemsTotal(r.itemsTotal())
                    .build();
            savedScaleScores.add(scaleScoreRepository.save(ss));
        }

        // ── Validity status (Task 10) ────────────────────────────────────────────
        // Inspect scale scores for validity-flagging scales (case-insensitive name match).
        // Rule: INVALID if a Consistency, Inconsistency, or Social_Desirability scale has
        // a non-null STEN ≤ 2.  Default is VALID.  Only set on the SCORED path (not PENDING).
        ValidityStatus vs = ValidityStatus.VALID;
        for (ScaleScoreResult r : output.scaleScores()) {
            PsychometricScale s = scaleById.get(r.scaleId());
            if (s == null) continue;
            String name = s.getName();
            if (name != null
                    && (name.equalsIgnoreCase("Consistency")
                            || name.equalsIgnoreCase("Inconsistency")
                            || name.equalsIgnoreCase("Social_Desirability"))
                    && r.stenScore() != null
                    && r.stenScore().compareTo(new BigDecimal("2")) <= 0) {
                vs = ValidityStatus.INVALID;
            }
        }
        result.setValidityStatus(vs);
        testResultRepository.save(result);

        // ── Competency scoring ───────────────────────────────────────────────────
        scoreCompetencies(result, savedScaleScores);

        log.info("ScoringService: scored session {} for test {} — {} scale scores",
                session.getId(), test.getId(), savedScaleScores.size());
    }

    /**
     * Re-scores an existing PENDING result after a scoring key has been published.
     * Called by the admin re-score endpoint.
     */
    @Transactional
    public void rescoreResult(UUID resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new IllegalArgumentException("TestResult not found: " + resultId));
        competencyScoreRepository.deleteByResultId(result.getId());
        scaleScoreRepository.deleteByResultId(result.getId());
        testResultRepository.delete(result);
        scoreSession(result.getSession());
    }

    // ── Config-mapping helpers ─────────────────────────────────────────────────────

    /**
     * Resolves the item scoring strategy: explicit {@code item_strategy} if set,
     * otherwise a default derived from the question type. ATP/VIP strategies
     * (BINARY_FORCED_CHOICE / OPTION_TAGGED_TALLY) cannot be inferred and must be
     * set explicitly on the scoring key.
     */
    private ItemStrategyType resolveStrategy(ScoringKeyItem item) {
        if (item.getItemStrategy() != null) {
            return item.getItemStrategy();
        }
        return switch (item.getQuestion().getQuestionType()) {
            case SCALE -> ItemStrategyType.LIKERT_VALUE;
            case CHOICE_SINGLE, FORCED_CHOICE -> ItemStrategyType.ANSWER_KEY_SINGLE;
            case CHOICE_MULTIPLE -> ItemStrategyType.ANSWER_KEY_MULTIPLE;
            case ADJECTIVE_CHECKLIST -> ItemStrategyType.ADJECTIVE_COUNT;
            default -> throw new IllegalStateException(
                    "No default item strategy for " + item.getQuestion().getQuestionType());
        };
    }

    /**
     * Builds an {@link ItemResponse} from the loaded answer entities for one question.
     * Returns {@code null} if the question was not answered (so it is omitted from the
     * response map; the calculator still counts it via {@code itemsTotal}).
     */
    private ItemResponse buildResponse(ItemStrategyType strategy, UUID questionId,
                                       AnswerScale scaleAnswer,
                                       List<AnswerChoice> choiceAnswers,
                                       AnswerAdjective adjectiveAnswer) {
        return switch (strategy) {
            case LIKERT_VALUE -> {
                if (scaleAnswer == null) yield null;
                yield new ItemResponse(questionId, scaleAnswer.getValue(),
                        scaleAnswer.getMinValue(), scaleAnswer.getMaxValue(),
                        null, null, null);
            }
            case BINARY_FORCED_CHOICE -> {
                // ATP: numeric value 1/2 derived from the selected option's ordinal.
                // displayOrder is 0-based → value = displayOrder + 1 (1 or 2).
                if (choiceAnswers.isEmpty()) yield null;
                int value = choiceAnswers.get(0).getCandidateAnswer().getDisplayOrder() + 1;
                yield new ItemResponse(questionId, value, 1, 2, null, null, null);
            }
            case ANSWER_KEY_SINGLE, ANSWER_KEY_MULTIPLE -> {
                if (choiceAnswers.isEmpty()) yield null;
                List<UUID> selected = choiceAnswers.stream()
                        .map(ac -> ac.getCandidateAnswer().getId())
                        .toList();
                yield new ItemResponse(questionId, null, null, null, selected, null, null);
            }
            case ADJECTIVE_COUNT -> {
                if (adjectiveAnswer == null) yield null;
                yield new ItemResponse(questionId, null, null, null, null, null,
                        adjectiveAnswer.getSelected().size());
            }
            case OPTION_TAGGED_TALLY -> {
                // VIP: resolve the tag-scale from the selected option's CandidateAnswer.tagScale.
                // CandidateAnswer is managed within the scoring transaction, so proxy init is safe.
                if (choiceAnswers.isEmpty()) yield null;
                CandidateAnswer selected = choiceAnswers.get(0).getCandidateAnswer();
                UUID tagScaleId = (selected.getTagScale() != null)
                        ? selected.getTagScale().getId() : null;
                yield new ItemResponse(questionId, null, null, null, null, tagScaleId, null);
            }
        };
    }

    /**
     * Builds a {@link NormConfig} for one scale from the active norm table, or returns
     * {@code null} if the scale is not normed.
     *
     * <ul>
     *   <li>PARAMETRIC → mean/sd + the t-score transform params from {@link NormScaleParam}.</li>
     *   <li>EMPIRICAL_PERCENTILE → percentile/sten buckets from {@link NormEntry} rows.</li>
     * </ul>
     */
    private NormConfig buildNormConfig(NormTableVersion norm, UUID scaleId) {
        if (norm == null) {
            return null;
        }
        NormStrategyType strategy = norm.getNormStrategy();
        if (strategy == NormStrategyType.EMPIRICAL_PERCENTILE) {
            List<NormEntry> entries = normEntryRepository.findByNormTableIdAndScaleId(norm.getId(), scaleId);
            if (entries.isEmpty()) {
                return null;
            }
            List<NormConfig.PercentileBucket> buckets = entries.stream()
                    .map(e -> new NormConfig.PercentileBucket(
                            e.getRawScoreMin(), e.getRawScoreMax(), e.getPercentile(), e.getStenScore()))
                    .toList();
            return new NormConfig(NormStrategyType.EMPIRICAL_PERCENTILE,
                    null, null, null, null, null, null, buckets);
        }
        // Default / PARAMETRIC
        Optional<NormScaleParam> paramOpt =
                normScaleParamRepository.findByNormTable_IdAndScale_Id(norm.getId(), scaleId);
        if (paramOpt.isEmpty()) {
            return null;
        }
        NormScaleParam p = paramOpt.get();
        return new NormConfig(NormStrategyType.PARAMETRIC,
                p.getMean(), p.getSd(),
                p.getTFactor(), p.getTOffset(), p.getTClipLo(), p.getTClipHi(),
                null);
    }

    // ── Persistence helpers ─────────────────────────────────────────────────────────

    private void persistPendingResult(PsychometricTest test, ResponseSession session) {
        TestResult result = TestResult.builder()
                .test(test)
                .session(session)
                .status(TestResultStatus.PENDING)
                .resultState(ResultState.NOT_YET_SCOREABLE)
                .focusLossCount(session.getFocusLossCount())
                .build();
        testResultRepository.save(result);
    }

    /**
     * Micro-engagement accrual (Phase 3, D3/D4).
     *
     * <p>Records {@link UserItemExposure} (answeredAt = now) for every question answered in this
     * session, then advances per-scale {@link ScaleProgress} for every CONSOLIDATED scale that has
     * scoring-key items. For a CONSOLIDATED scale the open ({@code COLLECTING}) window is reused if
     * one exists, otherwise a fresh window is opened — {@code itemsRequired} = the count of that
     * scale's scoring-key items, and the norm version is FROZEN ({@code normTableVersionId}) at the
     * live VALIDATED version when the window opens (D4). {@code itemsCollected} is incremented by the
     * number of that scale's items answered in this session.
     *
     * @return live {@link ScaleProgress} rows keyed by scaleId (only CONSOLIDATED scales appear).
     */
    private Map<UUID, ScaleProgress> accrueExposureAndProgress(
            ResponseSession session, PsychometricTest test, List<ScoringKeyItem> items,
            Map<UUID, ItemResponse> responsesByQuestion, Optional<NormTableVersion> normOpt,
            List<PsychometricScale> allScales) {

        UUID userId = (session.getUser() != null) ? session.getUser().getId() : null;
        if (userId == null) {
            // Anonymous sessions cannot accrue per-user progress — nothing to consolidate.
            return Map.of();
        }

        LocalDateTime now = LocalDateTime.now();

        // 1. Record exposure (answered) for every answered question — composite-PK upsert.
        for (UUID questionId : responsesByQuestion.keySet()) {
            UserItemExposure exposure = userItemExposureRepository
                    .findById(new UserItemExposureId(userId, questionId))
                    .orElseGet(() -> UserItemExposure.builder()
                            .userId(userId).questionId(questionId).testId(test.getId())
                            .firstSeen(now).build());
            exposure.setAnsweredAt(now);
            userItemExposureRepository.save(exposure);
        }

        // 2. Per-scale: total scoring-key items (= itemsRequired) and answered-this-session count.
        Map<UUID, Integer> itemsRequiredByScale = new HashMap<>();
        Map<UUID, Integer> answeredThisSessionByScale = new HashMap<>();
        for (ScoringKeyItem item : items) {
            UUID scaleId = item.getScale().getId();
            itemsRequiredByScale.merge(scaleId, 1, Integer::sum);
            if (responsesByQuestion.containsKey(item.getQuestion().getId())) {
                answeredThisSessionByScale.merge(scaleId, 1, Integer::sum);
            }
        }

        // 3. Advance progress for CONSOLIDATED scales only.
        Map<UUID, ScaleProgress> existingByScale = scaleProgressRepository
                .findByUserIdAndTestId(userId, test.getId()).stream()
                .filter(p -> p.getState() == ScaleProgressState.COLLECTING)
                .collect(Collectors.toMap(ScaleProgress::getScaleId, p -> p, (a, b) -> a));

        Map<UUID, ScaleProgress> progressByScale = new HashMap<>();
        for (PsychometricScale s : allScales) {
            if (s.getResultMode() != ResultMode.CONSOLIDATED) {
                continue;
            }
            int required = itemsRequiredByScale.getOrDefault(s.getId(), 0);
            if (required == 0) {
                continue; // scale has no scoring-key items — nothing to accrue
            }
            ScaleProgress p = existingByScale.get(s.getId());
            if (p == null) {
                // Open a new consolidation window; freeze the norm version (D4).
                p = ScaleProgress.builder()
                        .userId(userId).scaleId(s.getId()).testId(test.getId())
                        .windowId(UUID.randomUUID())
                        .normTableVersionId(normOpt.map(NormTableVersion::getId).orElse(null))
                        .itemsRequired(required)
                        .itemsCollected(0)
                        .state(ScaleProgressState.COLLECTING)
                        .openedAt(now)
                        .build();
            }
            int answered = answeredThisSessionByScale.getOrDefault(s.getId(), 0);
            if (answered > 0) {
                p.setItemsCollected(p.getItemsCollected() + answered);
            }
            p = scaleProgressRepository.save(p);
            progressByScale.put(s.getId(), p);
        }
        return progressByScale;
    }

    /**
     * Computes weighted competency scores from the scale sten scores just persisted.
     *
     * <p>Each competency aggregates the sten scores of its contributing scales weighted
     * by {@link CompetencyScaleWeight}. REVERSE direction inverts the sten ({@code 11 - sten}).
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
                        double stenValue = sten.doubleValue();
                        double raw = w.getDirection() == ScoreDirection.REVERSE
                                ? (11.0 - stenValue) : stenValue;
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
}
