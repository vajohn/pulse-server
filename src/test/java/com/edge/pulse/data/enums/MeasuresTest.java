package com.edge.pulse.data.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeasuresTest {

    @Test
    void definesTypicalMaximalDerived() {
        assertThat(Measures.values())
                .containsExactlyInAnyOrder(Measures.TYPICAL, Measures.MAXIMAL, Measures.DERIVED);
    }

    @Test
    void valueOfRoundTrips() {
        assertThat(Measures.valueOf("TYPICAL")).isEqualTo(Measures.TYPICAL);
        assertThat(Measures.valueOf("MAXIMAL")).isEqualTo(Measures.MAXIMAL);
        assertThat(Measures.valueOf("DERIVED")).isEqualTo(Measures.DERIVED);
    }
}
