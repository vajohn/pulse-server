package com.edge.pulse.services.psychometric.micro.model;

import java.util.UUID;

/** A scale's accrual state for sampling. remaining = itemsRequired − itemsCollected. */
public record SamplerScale(UUID scaleId, int itemsRequired, int itemsCollected) {
    public int remaining() { return Math.max(0, itemsRequired - itemsCollected); }
}
