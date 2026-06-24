package com.edge.pulse.services.psychometric.scoring.model;

import com.edge.pulse.data.enums.*;
import java.util.List;
import java.util.UUID;

public record ScaleConfig(
        UUID scaleId,
        String name,
        UUID parentScaleId,
        ScoreMethod scoreMethod,
        CompositeMethod compositeMethod,   // null = leaf
        CompositeBasis compositeBasis,     // for AGGREGATE_OF_CHILDREN_*
        List<UUID> childScaleIds,
        NormConfig norm                    // null if scale is not normed
) {}
