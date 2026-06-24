package com.edge.pulse.services.psychometric.scoring.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ScoringInput(
        List<ScaleConfig> scales,
        List<ItemConfig> items,
        Map<UUID, ItemResponse> responsesByQuestion
) {}
