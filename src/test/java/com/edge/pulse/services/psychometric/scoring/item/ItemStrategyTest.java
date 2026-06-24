package com.edge.pulse.services.psychometric.scoring.item;

import com.edge.pulse.data.enums.ItemStrategyType;
import com.edge.pulse.data.enums.ScoreDirection;
import com.edge.pulse.services.psychometric.scoring.model.ItemConfig;
import com.edge.pulse.services.psychometric.scoring.model.ItemResponse;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ItemStrategyTest {
    final UUID q = UUID.randomUUID();
    final UUID scale = UUID.randomUUID();

    ItemConfig cfg(ItemStrategyType s, ScoreDirection d) {
        return new ItemConfig(q, scale, s, d, 1.0, null, null, false, null);
    }

    @Test
    void likertForward_returnsValue() {
        var r = new ItemResponse(q, 3, 1, 5, null, null, null);
        assertThat(ItemStrategies.of(ItemStrategyType.LIKERT_VALUE)
                .score(cfg(ItemStrategyType.LIKERT_VALUE, ScoreDirection.FORWARD), r)).isEqualTo(3.0);
    }

    @Test
    void likertReverse_invertsAroundRange() {
        var r = new ItemResponse(q, 2, 1, 5, null, null, null); // 5+1-2 = 4
        assertThat(ItemStrategies.of(ItemStrategyType.LIKERT_VALUE)
                .score(cfg(ItemStrategyType.LIKERT_VALUE, ScoreDirection.REVERSE), r)).isEqualTo(4.0);
    }

    @Test
    void binaryForcedChoice_forward_1to1_2to0() {
        var s = ItemStrategies.of(ItemStrategyType.BINARY_FORCED_CHOICE);
        var c = cfg(ItemStrategyType.BINARY_FORCED_CHOICE, ScoreDirection.FORWARD);
        assertThat(s.score(c, new ItemResponse(q, 1, 1, 2, null, null, null))).isEqualTo(1.0);
        assertThat(s.score(c, new ItemResponse(q, 2, 1, 2, null, null, null))).isEqualTo(0.0);
    }

    @Test
    void binaryForcedChoice_reverse_1to0_2to1() {
        var s = ItemStrategies.of(ItemStrategyType.BINARY_FORCED_CHOICE);
        var c = cfg(ItemStrategyType.BINARY_FORCED_CHOICE, ScoreDirection.REVERSE);
        assertThat(s.score(c, new ItemResponse(q, 1, 1, 2, null, null, null))).isEqualTo(0.0);
        assertThat(s.score(c, new ItemResponse(q, 2, 1, 2, null, null, null))).isEqualTo(1.0);
    }

    @Test
    void answerKeySingle_correctIs1_wrongIs0() {
        UUID correct = UUID.randomUUID(), wrong = UUID.randomUUID();
        var c = new ItemConfig(q, scale, ItemStrategyType.ANSWER_KEY_SINGLE,
                ScoreDirection.FORWARD, 1.0, correct, null, false, null);
        var s = ItemStrategies.of(ItemStrategyType.ANSWER_KEY_SINGLE);
        assertThat(s.score(c, new ItemResponse(q, null, null, null, List.of(correct), null, null))).isEqualTo(1.0);
        assertThat(s.score(c, new ItemResponse(q, null, null, null, List.of(wrong), null, null))).isEqualTo(0.0);
    }

    @Test
    void unanswered_returnsNaN() {
        var r = new ItemResponse(q, null, null, null, null, null, null);
        assertThat(ItemStrategies.of(ItemStrategyType.LIKERT_VALUE)
                .score(cfg(ItemStrategyType.LIKERT_VALUE, ScoreDirection.FORWARD), r)).isNaN();
    }

    // Fix 1: OptionTaggedTally — non-matching scale returns NaN, matching returns 1.0
    @Test
    void optionTaggedTally_scaleMatchesTag_returns1() {
        UUID tagScale = UUID.randomUUID();
        var item = new ItemConfig(q, tagScale, ItemStrategyType.OPTION_TAGGED_TALLY,
                ScoreDirection.FORWARD, 1.0, null, null, false, null);
        var r = new ItemResponse(q, null, null, null, null, tagScale, null);
        assertThat(ItemStrategies.of(ItemStrategyType.OPTION_TAGGED_TALLY).score(item, r)).isEqualTo(1.0);
    }

    @Test
    void optionTaggedTally_scaleDiffersFromTag_returnsNaN() {
        UUID itemScale = UUID.randomUUID();
        UUID otherScale = UUID.randomUUID();
        var item = new ItemConfig(q, itemScale, ItemStrategyType.OPTION_TAGGED_TALLY,
                ScoreDirection.FORWARD, 1.0, null, null, false, null);
        var r = new ItemResponse(q, null, null, null, null, otherScale, null);
        assertThat(ItemStrategies.of(ItemStrategyType.OPTION_TAGGED_TALLY).score(item, r)).isNaN();
    }

    @Test
    void optionTaggedTally_nullTagScaleId_returnsNaN() {
        var item = new ItemConfig(q, scale, ItemStrategyType.OPTION_TAGGED_TALLY,
                ScoreDirection.FORWARD, 1.0, null, null, false, null);
        var r = new ItemResponse(q, null, null, null, null, null, null);
        assertThat(ItemStrategies.of(ItemStrategyType.OPTION_TAGGED_TALLY).score(item, r)).isNaN();
    }

    // Fix 4: LikertValue REVERSE with null scaleMax/scaleMin returns NaN
    @Test
    void likertReverse_nullScaleMax_returnsNaN() {
        var r = new ItemResponse(q, 2, null, null, null, null, null);
        assertThat(ItemStrategies.of(ItemStrategyType.LIKERT_VALUE)
                .score(cfg(ItemStrategyType.LIKERT_VALUE, ScoreDirection.REVERSE), r)).isNaN();
    }

    // Fix 5: BinaryForcedChoice out-of-range (e.g. 3) returns NaN
    @Test
    void binaryForcedChoice_outOfRange_returnsNaN() {
        var r = new ItemResponse(q, 3, 1, 2, null, null, null);
        assertThat(ItemStrategies.of(ItemStrategyType.BINARY_FORCED_CHOICE)
                .score(cfg(ItemStrategyType.BINARY_FORCED_CHOICE, ScoreDirection.FORWARD), r)).isNaN();
    }
}
