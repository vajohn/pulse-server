package com.edge.pulse.data.dto.psychometric.imports;

import java.util.List;
import java.util.UUID;

/**
 * Result of an assessment-package import.
 *
 * <p>On success: {@code success=true}, {@code testId} set, counts populated, {@code errors} empty.
 * On a refuse-partial parse/validation failure the controller returns a 422 with
 * {@code success=false}, {@code testId=null} and the column-level {@code errors} list.
 */
public record ImportResultDto(
        boolean success,
        UUID testId,
        int questions,
        int scales,
        int items,
        int normParams,
        List<ImportError> errors
) {}
