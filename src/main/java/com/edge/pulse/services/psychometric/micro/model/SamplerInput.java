package com.edge.pulse.services.psychometric.micro.model;

import java.util.List;

public record SamplerInput(
        List<SamplerItem> items,
        List<SamplerScale> scales,
        int maxItems,
        long seed) {}
