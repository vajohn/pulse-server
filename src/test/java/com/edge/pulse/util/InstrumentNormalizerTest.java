package com.edge.pulse.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstrumentNormalizerTest {

    @Test
    void bigFiveVariantsCollapseToOneCanonical() {
        String expected = "big five";
        assertThat(InstrumentNormalizer.canonical("BigFive")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("big_five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big-Five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big  Five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("  Big Five  ")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big.Five")).isEqualTo(expected);
    }

    @Test
    void preservesAlphanumericTokens() {
        assertThat(InstrumentNormalizer.canonical("PTI Plus 2.0")).isEqualTo("pti plus 2 0");
        assertThat(InstrumentNormalizer.canonical("CA.b")).isEqualTo("ca b");
    }

    @Test
    void distinctNamesStayDistinct() {
        assertThat(InstrumentNormalizer.canonical("Verbal Reasoning"))
                .isNotEqualTo(InstrumentNormalizer.canonical("Numerical Reasoning"));
    }

    @Test
    void nullInNullOut() {
        assertThat(InstrumentNormalizer.canonical(null)).isNull();
    }

    @Test
    void blankInBlankOut() {
        assertThat(InstrumentNormalizer.canonical("   ")).isEmpty();
        assertThat(InstrumentNormalizer.canonical("---")).isEmpty();
    }
}
