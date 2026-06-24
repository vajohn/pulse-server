package com.edge.pulse.services.psychometric.scoring;

import com.edge.pulse.data.enums.CompositeMethod;
import com.edge.pulse.data.enums.ScoreMethod;
import com.edge.pulse.services.psychometric.scoring.composite.CompositeStrategies;
import com.edge.pulse.services.psychometric.scoring.item.ItemStrategies;
import com.edge.pulse.services.psychometric.scoring.model.*;
import com.edge.pulse.services.psychometric.scoring.norm.NormStrategies;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure scoring orchestration: config + responses -> per-scale scores. No JPA, no Spring. */
public class ScoringCalculator {

    public ScoringOutput calculate(ScoringInput in) {
        Map<UUID, ScaleConfig> scaleById = new LinkedHashMap<>();
        in.scales().forEach(s -> scaleById.put(s.scaleId(), s));

        // 1. Accumulate leaf raw scores: scaleId -> [weightedSum, weightSum, answered, total]
        Map<UUID, double[]> acc = new LinkedHashMap<>();
        for (ItemConfig item : in.items()) {
            ItemResponse r = in.responsesByQuestion().get(item.questionId());
            double v = ItemStrategies.of(item.strategy()).score(item, r);
            double[] b = acc.computeIfAbsent(item.scaleId(), k -> new double[4]);
            if (!Double.isNaN(v)) { b[0] += v * item.weight(); b[1] += item.weight(); b[2] += 1; }
            b[3] += 1;
        }

        // 2. Leaf raw -> finalize (SUM or MEAN)
        Map<UUID, BigDecimal> rawByScale = new HashMap<>();
        Map<UUID, int[]> countsByScale = new HashMap<>();
        for (var e : acc.entrySet()) {
            ScaleConfig sc = scaleById.get(e.getKey());
            double[] b = e.getValue();
            double raw = (b[2] == 0) ? 0.0
                    : (sc != null && sc.scoreMethod() == ScoreMethod.MEAN && b[1] > 0) ? b[0] / b[1] : b[0];
            rawByScale.put(e.getKey(), BigDecimal.valueOf(raw).setScale(3, RoundingMode.HALF_UP));
            countsByScale.put(e.getKey(), new int[]{(int) b[2], (int) b[3]});
        }

        // 3. AGGREGATE_OF_ITEMS parents: raw = sum of children raw (topological)
        rollupRawComposites(in.scales(), rawByScale, countsByScale);

        // 4. Standardize every scale that has a raw score + a norm (leaves and AGGREGATE_OF_ITEMS)
        Map<UUID, ScaleScoreResult> results = new LinkedHashMap<>();
        for (ScaleConfig sc : in.scales()) {
            if (sc.compositeMethod() != null && sc.compositeMethod() != CompositeMethod.AGGREGATE_OF_ITEMS) continue;
            BigDecimal raw = rawByScale.get(sc.scaleId());
            if (raw == null) continue;
            int[] c = countsByScale.getOrDefault(sc.scaleId(), new int[]{0, 0});
            if (sc.norm() != null) {
                results.put(sc.scaleId(), NormStrategies.of(sc.norm().strategy())
                        .standardize(sc.scaleId(), raw, c[0], c[1], sc.norm()));
            } else {
                results.put(sc.scaleId(), new ScaleScoreResult(sc.scaleId(), raw, null, null, null, null, c[0], c[1]));
            }
        }

        // 5. AGGREGATE_OF_CHILDREN composites over standardized children (topological)
        rollupStandardizedComposites(in.scales(), scaleById, results);

        return new ScoringOutput(new ArrayList<>(results.values()));
    }

    private void rollupRawComposites(List<ScaleConfig> scales, Map<UUID, BigDecimal> raw, Map<UUID, int[]> counts) {
        Map<UUID, List<UUID>> children = new HashMap<>();
        for (ScaleConfig s : scales)
            if (s.parentScaleId() != null)
                children.computeIfAbsent(s.parentScaleId(), k -> new ArrayList<>()).add(s.scaleId());
        Map<UUID, ScaleConfig> byId = new HashMap<>();
        scales.forEach(s -> byId.put(s.scaleId(), s));
        Set<UUID> targets = new LinkedHashSet<>();
        for (ScaleConfig s : scales)
            if (s.compositeMethod() == CompositeMethod.AGGREGATE_OF_ITEMS) targets.add(s.scaleId());
        Set<UUID> done = new HashSet<>(raw.keySet());
        boolean progress = true;
        while (!targets.isEmpty() && progress) {
            progress = false;
            for (Iterator<UUID> it = targets.iterator(); it.hasNext();) {
                UUID p = it.next();
                List<UUID> kids = children.getOrDefault(p, List.of());
                if (done.containsAll(kids)) {
                    BigDecimal sum = kids.stream().map(k -> raw.getOrDefault(k, BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    raw.put(p, sum.setScale(3, RoundingMode.HALF_UP));
                    int ans = kids.stream().mapToInt(k -> counts.getOrDefault(k, new int[]{0,0})[0]).sum();
                    int tot = kids.stream().mapToInt(k -> counts.getOrDefault(k, new int[]{0,0})[1]).sum();
                    counts.put(p, new int[]{ans, tot});
                    done.add(p); it.remove(); progress = true;
                }
            }
        }
    }

    private void rollupStandardizedComposites(List<ScaleConfig> scales, Map<UUID, ScaleConfig> byId,
                                              Map<UUID, ScaleScoreResult> results) {
        Set<UUID> targets = new LinkedHashSet<>();
        for (ScaleConfig s : scales)
            if (s.compositeMethod() == CompositeMethod.AGGREGATE_OF_CHILDREN_MEAN
             || s.compositeMethod() == CompositeMethod.AGGREGATE_OF_CHILDREN_SUM) targets.add(s.scaleId());
        boolean progress = true;
        while (!targets.isEmpty() && progress) {
            progress = false;
            for (Iterator<UUID> it = targets.iterator(); it.hasNext();) {
                UUID p = it.next();
                ScaleConfig sc = byId.get(p);
                if (results.keySet().containsAll(sc.childScaleIds())) {
                    List<ScaleScoreResult> kids = sc.childScaleIds().stream().map(results::get).toList();
                    results.put(p, CompositeStrategies.of(sc.compositeMethod())
                            .combine(p, sc.compositeBasis(), kids));
                    it.remove(); progress = true;
                }
            }
        }
    }
}
