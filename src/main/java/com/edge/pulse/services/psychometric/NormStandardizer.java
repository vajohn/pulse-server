package com.edge.pulse.services.psychometric;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Static Classical-Test-Theory norm formulas. Pure functions, no Spring/DB.
 *
 * <p>The psychometrics team supplies parametric-normal norms — a {@code mean} and {@code sd}
 * per scale. A scale's raw score is standardised against those, then converted to a STEN
 * (1–10) and a percentile. These formulas are <strong>static</strong> by contract; only the
 * (mean, sd) inputs change (~every 6 months). Golden vectors in the norm-refresh files are the
 * final authority and pin these implementations at import time.
 */
public final class NormStandardizer {

    private NormStandardizer() {}

    /** z = (raw - mean) / sd, scale 3. */
    public static BigDecimal zScore(BigDecimal raw, BigDecimal mean, BigDecimal sd) {
        return raw.subtract(mean).divide(sd, 6, RoundingMode.HALF_UP).setScale(3, RoundingMode.HALF_UP);
    }

    /** sten = clamp(round_half_up(5.5 + 2z), 1, 10). */
    public static int sten(BigDecimal z) {
        BigDecimal raw = new BigDecimal("5.5").add(z.multiply(BigDecimal.valueOf(2)));
        int sten = raw.setScale(0, RoundingMode.HALF_UP).intValue();
        if (sten < 1) return 1;
        if (sten > 10) return 10;
        return sten;
    }

    /** percentile = Phi(z) * 100, HALF_UP to 2 decimals. */
    public static BigDecimal percentile(BigDecimal z) {
        double phi = 0.5 * (1.0 + erf(z.doubleValue() / Math.sqrt(2.0)));
        return BigDecimal.valueOf(phi * 100.0).setScale(2, RoundingMode.HALF_UP);
    }

    /** STEN to one decimal, clamped to [1,10], round-half-even (matches numpy .round(1)). */
    public static BigDecimal stenDecimal(BigDecimal z) {
        BigDecimal raw = new BigDecimal("5.5").add(z.multiply(BigDecimal.valueOf(2)));
        BigDecimal clamped = raw.max(BigDecimal.ONE).min(BigDecimal.TEN);
        return clamped.setScale(1, java.math.RoundingMode.HALF_EVEN);
    }

    /** T = clamp(z*factor + offset, lo, hi), one decimal, round-half-even. */
    public static BigDecimal tScore(BigDecimal z, BigDecimal factor, BigDecimal offset,
                                    BigDecimal clipLo, BigDecimal clipHi) {
        BigDecimal raw = z.multiply(factor).add(offset);
        BigDecimal clamped = raw.max(clipLo).min(clipHi);
        return clamped.setScale(1, java.math.RoundingMode.HALF_EVEN);
    }

    /**
     * Abramowitz & Stegun 7.1.26 approximation of the error function.
     * Max absolute error 1.5e-7 — negligible at 2-decimal percentile precision.
     */
    private static double erf(double x) {
        double sign = Math.signum(x);
        double ax = Math.abs(x);
        double t = 1.0 / (1.0 + 0.3275911 * ax);
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-ax * ax);
        return sign * y;
    }
}
