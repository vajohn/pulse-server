package com.edge.pulse.data.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TestTypeCapabilitiesTest {

    @Test
    void everyTestTypeHasAProfile() {
        for (TestType type : TestType.values()) {
            assertThat(TestTypeCapabilities.of(type)).isNotNull(); // must not throw
        }
    }

    @Test
    void competencyIsDerivedWithNoItemsAndUntimed() {
        TestTypeCapabilities c = TestTypeCapabilities.of(TestType.COMPETENCY);
        assertThat(c.measures).isEqualTo(Measures.DERIVED);
        assertThat(c.allowedQuestionTypes).isEmpty();
        assertThat(c.timeLimitRequired).isFalse();
        assertThat(c.timeLimitVisible).isFalse();
        assertThat(c.displayLabel).isEqualTo("Competency");
        assertThat(c.description).isNotBlank();
        assertThat(c.exampleInstruments).isNotEmpty();
    }

    @Test
    void personalityIsTypicalSelfReport() {
        TestTypeCapabilities p = TestTypeCapabilities.of(TestType.PERSONALITY);
        assertThat(p.measures).isEqualTo(Measures.TYPICAL);
        assertThat(p.timeLimitVisible).isFalse();
        assertThat(p.allowedQuestionTypes).contains(QuestionType.SCALE, QuestionType.FORCED_CHOICE);
        assertThat(p.exampleInstruments).contains("Big Five", "Adaptive Traits Profiler", "PTI Plus");
    }

    @Test
    void cognitiveIsMaximalTimed() {
        TestTypeCapabilities c = TestTypeCapabilities.of(TestType.COGNITIVE);
        assertThat(c.measures).isEqualTo(Measures.MAXIMAL);
        assertThat(c.timeLimitRequired).isTrue();
        assertThat(c.timeLimitVisible).isTrue();
        assertThat(c.allowedQuestionTypes)
                .contains(QuestionType.CHOICE_SINGLE, QuestionType.CHOICE_MULTIPLE);
    }

    @Test
    void capabilitiesEnumStaysInSyncWithTestType() {
        // Each TestType maps to a same-named capability constant and vice-versa.
        assertThat(Arrays.stream(TestTypeCapabilities.values()).map(Enum::name))
                .containsExactlyInAnyOrder(
                        Arrays.stream(TestType.values()).map(Enum::name).toArray(String[]::new));
    }
}
