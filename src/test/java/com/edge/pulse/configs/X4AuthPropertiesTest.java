package com.edge.pulse.configs;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class X4AuthPropertiesTest {
    @Test
    void matchModeDefaultsToEmail() {
        assertThat(new X4AuthProperties().getMatchMode()).isEqualTo("EMAIL");
    }
}
