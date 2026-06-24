package com.edge.pulse.data.dto.psychometric.imports;

import java.math.BigDecimal;
import java.util.UUID;

public record NormScaleParamRequest(
        UUID scaleId,
        BigDecimal mean, BigDecimal sd,
        BigDecimal tFactor, BigDecimal tOffset, BigDecimal tClipLo, BigDecimal tClipHi,
        Integer sampleSize
) {}
