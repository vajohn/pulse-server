package com.edge.pulse.data.dto.psychometric;

import com.edge.pulse.data.enums.Cadence;
import java.util.UUID;

/** A pull-delivery check-in available to a candidate. progress = consolidated scales / total
 *  CONSOLIDATED scales for this user+test (drives the "N of M" building-profile copy). */
public record CheckInDto(
        UUID cadenceId,
        UUID testId,
        UUID formId,
        String testName,
        Cadence cadence,
        int maxItems,
        int scalesConsolidated,
        int scalesTotal) {}
