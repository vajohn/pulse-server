package com.edge.pulse.data.enums;

/**
 * What a test type measures, in psychometric terms — used purely for self-documenting UI labels.
 *
 * <ul>
 *   <li>{@link #TYPICAL} — typical behaviour / traits / interests (self-report; no right or wrong).</li>
 *   <li>{@link #MAXIMAL} — maximal performance (right/wrong, typically timed).</li>
 *   <li>{@link #DERIVED} — derived from other tests' scale scores (no items of its own).</li>
 * </ul>
 */
public enum Measures {
    TYPICAL,
    MAXIMAL,
    DERIVED
}
