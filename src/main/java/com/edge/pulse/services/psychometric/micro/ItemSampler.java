package com.edge.pulse.services.psychometric.micro;

import com.edge.pulse.services.psychometric.micro.model.SamplerInput;
import com.edge.pulse.services.psychometric.micro.model.SamplerItem;
import com.edge.pulse.services.psychometric.micro.model.SamplerScale;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Pure, deterministic item sampler for micro-engagement (D5).
 *
 * <p>Draws ≤{@code maxItems} question ids for the next micro-set from the UNSEEN pool only —
 * an already-seen item is NEVER re-delivered (no padding). Among unseen items, ordering is:
 * <ol>
 *   <li>scale nearest completion first (smallest remaining items_required − items_collected);</li>
 *   <li>a seeded shuffle within the same priority bucket so ties are stable per seed.</li>
 * </ol>
 * If fewer than {@code maxItems} unseen items remain, fewer are returned. Completed scales
 * (remaining == 0) contribute nothing. No JPA / Spring state — fully unit-testable; annotated
 * {@link Component} only so it can be injected as a stateless bean.
 */
@Component
public class ItemSampler {

    public List<UUID> next(SamplerInput in) {
        Map<UUID, Integer> remainingByScale = new HashMap<>();
        for (SamplerScale s : in.scales()) {
            remainingByScale.put(s.scaleId(), s.remaining());
        }

        // Keep only UNSEEN items whose scale still needs more items. Seen items are NEVER
        // re-delivered — we never pad up to maxItems with already-seen ids (Fix C).
        List<SamplerItem> eligible = new ArrayList<>();
        for (SamplerItem item : in.items()) {
            if (item.seen()) continue;
            int remaining = remainingByScale.getOrDefault(item.scaleId(), 0);
            if (remaining > 0) eligible.add(item);
        }

        // Seeded shuffle first so within-bucket order is deterministic-but-varied per seed.
        Random rng = new Random(in.seed());
        Collections.shuffle(eligible, rng);

        // Stable sort by scale-remaining asc (nearest completion first); shuffle is the tie-break.
        eligible.sort(Comparator
                .comparingInt((SamplerItem i) -> remainingByScale.getOrDefault(i.scaleId(), 0)));

        int n = Math.max(0, Math.min(in.maxItems(), eligible.size()));
        List<UUID> picked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) picked.add(eligible.get(i).questionId());
        return picked;
    }
}
