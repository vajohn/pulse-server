package com.edge.pulse.services.psychometric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NormStandardizerTest {

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    // ── z-score ──────────────────────────────────────────────────────────────
    @Test
    void zScore_isStandardisedDistanceFromMean() {
        assertThat(NormStandardizer.zScore(bd("70"), bd("50"), bd("10")))
                .isEqualByComparingTo("2.000");
        assertThat(NormStandardizer.zScore(bd("50"), bd("50"), bd("10")))
                .isEqualByComparingTo("0.000");
        assertThat(NormStandardizer.zScore(bd("30"), bd("50"), bd("10")))
                .isEqualByComparingTo("-2.000");
    }

    // ── STEN: round(5.5 + 2z), clamp 1..10 ─────────────────────────────────────
    @Test
    void sten_clampsAndRoundsHalfUp() {
        assertThat(NormStandardizer.sten(bd("0.000"))).isEqualTo(6);   // 5.5 -> HALF_UP 6
        assertThat(NormStandardizer.sten(bd("2.000"))).isEqualTo(10);  // 9.5 -> 10
        assertThat(NormStandardizer.sten(bd("-2.000"))).isEqualTo(2);  // 1.5 -> 2
        assertThat(NormStandardizer.sten(bd("4.000"))).isEqualTo(10);  // 13.5 -> clamp 10
        assertThat(NormStandardizer.sten(bd("-4.000"))).isEqualTo(1);  // -2.5 -> clamp 1
    }

    // ── percentile: Phi(z)*100, 2dp ────────────────────────────────────────────
    @Test
    void percentile_matchesNormalCdf() {
        assertThat(NormStandardizer.percentile(bd("0.000")))
                .isEqualByComparingTo("50.00");
        assertThat(NormStandardizer.percentile(bd("2.000")))
                .isEqualByComparingTo("97.72");
        assertThat(NormStandardizer.percentile(bd("-2.000")))
                .isEqualByComparingTo("2.28");
        assertThat(NormStandardizer.percentile(bd("4.000")))
                .isEqualByComparingTo("100.00");
    }

    // ── Golden vector: raw/mean/sd straight through ─────────────────────────────
    @Test
    void goldenVector_raw70_mean50_sd10() {
        BigDecimal z = NormStandardizer.zScore(bd("70"), bd("50"), bd("10"));
        assertThat(NormStandardizer.sten(z)).isEqualTo(10);
        assertThat(NormStandardizer.percentile(z)).isEqualByComparingTo("97.72");
    }
}
