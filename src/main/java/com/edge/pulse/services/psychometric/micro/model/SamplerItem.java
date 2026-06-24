package com.edge.pulse.services.psychometric.micro.model;

import java.util.UUID;

/** A candidate item for sampling. seen = already in user_item_exposure for this user+test. */
public record SamplerItem(UUID questionId, UUID scaleId, boolean seen) {}
