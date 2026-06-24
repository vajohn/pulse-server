package com.edge.pulse.services.psychometric.scoring.parity;

import com.edge.pulse.services.psychometric.scoring.ScoringCalculator;
import com.edge.pulse.services.psychometric.scoring.model.ScaleScoreResult;
import com.edge.pulse.services.psychometric.scoring.model.ScoringOutput;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared parity-assertion driver: runs the engine over every fixture user/scale and diffs vs gold. */
final class ParityAsserter {

    private ParityAsserter() {}

    static void assertParity(String label, String dir) {
        ParityFixtureLoader.Fixture fx = ParityFixtureLoader.load(dir);
        ScoringCalculator calc = new ScoringCalculator();
        Map<String, UUID> scaleId = fx.scaleIdByName;

        int comparisons = 0;
        StringBuilder failures = new StringBuilder();

        for (String user : fx.userOrder) {
            ScoringOutput out = calc.calculate(fx.inputFor(user));
            Map<UUID, ScaleScoreResult> byId = new LinkedHashMap<>();
            out.scaleScores().forEach(r -> byId.put(r.scaleId(), r));

            Map<String, BigDecimal> expSten = fx.expectedSten.get(user);
            Map<String, BigDecimal> expT = fx.expectedT.get(user);

            java.util.Set<String> allScales = new java.util.LinkedHashSet<>(expSten.keySet());
            allScales.addAll(expT.keySet());

            for (String scale : allScales) {
                UUID id = scaleId.get(scale);
                assertThat(id).as("config defines scale %s", scale).isNotNull();
                ScaleScoreResult r = byId.get(id);
                assertThat(r).as("engine produced result for %s/%s", user, scale).isNotNull();

                BigDecimal es = expSten.get(scale);
                if (es != null) {
                    comparisons++;
                    if (r.stenScore() == null || r.stenScore().compareTo(es) != 0) {
                        failures.append(String.format("%s %s STEN: got %s expected %s%n",
                                user, scale, r.stenScore(), es));
                    }
                }
                BigDecimal et = expT.get(scale);
                if (et != null) {
                    comparisons++;
                    if (r.tScore() == null || r.tScore().compareTo(et) != 0) {
                        failures.append(String.format("%s %s T: got %s expected %s%n",
                                user, scale, r.tScore(), et));
                    }
                }
            }
        }

        System.out.printf("%s parity: %d users, %d scale comparisons%n",
                label, fx.userOrder.size(), comparisons);
        assertThat(failures.toString())
                .as("%s parity mismatches (got vs expected)", label)
                .isEmpty();
    }
}
