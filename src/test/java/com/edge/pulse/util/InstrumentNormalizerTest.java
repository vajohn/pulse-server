package com.edge.pulse.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InstrumentNormalizerTest {

    @Test
    void bigFiveVariantsCollapseToOneCanonical() {
        // All separator/casing/spacing variants — including no-separator — must produce identical output.
        String expected = "bigfive";
        assertThat(InstrumentNormalizer.canonical("BigFive")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("big_five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big-Five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big  Five")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("  Big Five  ")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("Big.Five")).isEqualTo(expected);
        // Key regression: no-separator variant that previously produced "bigfive" != "big five"
        assertThat(InstrumentNormalizer.canonical("bigfive")).isEqualTo(expected);
        assertThat(InstrumentNormalizer.canonical("BIG FIVE")).isEqualTo(expected);
    }

    @Test
    void preservesAlphanumericTokens() {
        // All separators stripped — "PTI Plus 2.0" → "ptiplus20"
        assertThat(InstrumentNormalizer.canonical("PTI Plus 2.0")).isEqualTo("ptiplus20");
        assertThat(InstrumentNormalizer.canonical("CA.b")).isEqualTo("cab");
    }

    @Test
    void distinctNamesStayDistinct() {
        assertThat(InstrumentNormalizer.canonical("Verbal Reasoning"))
                .isNotEqualTo(InstrumentNormalizer.canonical("Numerical Reasoning"));
        // big five vs big six must still differ
        assertThat(InstrumentNormalizer.canonical("Big Five"))
                .isNotEqualTo(InstrumentNormalizer.canonical("Big Six"));
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
