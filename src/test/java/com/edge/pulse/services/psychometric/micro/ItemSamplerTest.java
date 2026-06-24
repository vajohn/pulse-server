package com.edge.pulse.services.psychometric.micro;

import com.edge.pulse.services.psychometric.micro.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ItemSamplerTest {

    private final ItemSampler sampler = new ItemSampler();

    private static UUID id(String s) { return UUID.nameUUIDFromBytes(s.getBytes()); }

    @Test
    void drawsAtMostMaxItems() {
        UUID sc = id("scale-a");
        List<SamplerItem> items = List.of(
                new SamplerItem(id("q1"), sc, false),
                new SamplerItem(id("q2"), sc, false),
                new SamplerItem(id("q3"), sc, false),
                new SamplerItem(id("q4"), sc, false));
        var out = sampler.next(new SamplerInput(items, List.of(new SamplerScale(sc, 10, 0)), 2, 42L));
        assertThat(out).hasSize(2);
    }

    @Test
    void prefersUnseenItemsOverSeen() {
        UUID sc = id("scale-a");
        List<SamplerItem> items = List.of(
                new SamplerItem(id("seen1"), sc, true),
                new SamplerItem(id("seen2"), sc, true),
                new SamplerItem(id("unseen1"), sc, false),
                new SamplerItem(id("unseen2"), sc, false));
        var out = sampler.next(new SamplerInput(items, List.of(new SamplerScale(sc, 10, 0)), 2, 42L));
        assertThat(out).containsExactlyInAnyOrder(id("unseen1"), id("unseen2"));
    }

    @Test
    void neverReDeliversSeenItems_evenWhenMaxItemsExceedsUnseenCount() {
        // Fix C: padding with already-seen items is forbidden. With 2 unseen and 3 seen items
        // and maxItems=5, the sampler must return ONLY the 2 unseen ids (≤ unseen-count), never
        // padding up to 5 with seen items.
        UUID sc = id("scale-a");
        List<SamplerItem> items = List.of(
                new SamplerItem(id("seen1"), sc, true),
                new SamplerItem(id("seen2"), sc, true),
                new SamplerItem(id("seen3"), sc, true),
                new SamplerItem(id("unseen1"), sc, false),
                new SamplerItem(id("unseen2"), sc, false));
        var out = sampler.next(new SamplerInput(items, List.of(new SamplerScale(sc, 10, 0)), 5, 42L));
        assertThat(out).containsExactlyInAnyOrder(id("unseen1"), id("unseen2"));
        assertThat(out).doesNotContain(id("seen1"), id("seen2"), id("seen3"));
        assertThat(out.size()).isLessThanOrEqualTo(2); // ≤ unseen-count, never padded to maxItems
    }

    @Test
    void returnsEmptyWhenAllEligibleItemsAreSeen() {
        // Fix C: an open scale with only seen items left yields nothing (no re-delivery).
        UUID sc = id("scale-a");
        List<SamplerItem> items = List.of(
                new SamplerItem(id("seen1"), sc, true),
                new SamplerItem(id("seen2"), sc, true));
        var out = sampler.next(new SamplerInput(items, List.of(new SamplerScale(sc, 10, 1)), 5, 42L));
        assertThat(out).isEmpty();
    }

    @Test
    void prefersScaleNearestCompletionWhenUnseenAcrossScales() {
        UUID near = id("near");   // needs 1 more
        UUID far  = id("far");    // needs 8 more
        List<SamplerItem> items = List.of(
                new SamplerItem(id("near-q"), near, false),
                new SamplerItem(id("far-q1"), far, false),
                new SamplerItem(id("far-q2"), far, false));
        List<SamplerScale> scales = List.of(
                new SamplerScale(near, 5, 4),   // remaining 1 → highest priority
                new SamplerScale(far, 10, 2));  // remaining 8
        var out = sampler.next(new SamplerInput(items, scales, 1, 42L));
        assertThat(out).containsExactly(id("near-q"));
    }

    @Test
    void deterministicGivenSameSeed() {
        UUID sc = id("scale-a");
        List<SamplerItem> items = List.of(
                new SamplerItem(id("q1"), sc, false),
                new SamplerItem(id("q2"), sc, false),
                new SamplerItem(id("q3"), sc, false));
        var in = new SamplerInput(items, List.of(new SamplerScale(sc, 10, 0)), 2, 99L);
        assertThat(sampler.next(in)).isEqualTo(sampler.next(in));
    }

    @Test
    void skipsCompletedScalesAndReturnsEmptyWhenNothingLeft() {
        UUID sc = id("done");
        List<SamplerItem> items = List.of(new SamplerItem(id("q1"), sc, true));
        // scale already complete (remaining 0) and only seen items → nothing useful to draw
        var out = sampler.next(new SamplerInput(items, List.of(new SamplerScale(sc, 5, 5)), 5, 1L));
        assertThat(out).isEmpty();
    }
}
