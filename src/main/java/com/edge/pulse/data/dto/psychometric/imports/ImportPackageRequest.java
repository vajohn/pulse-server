package com.edge.pulse.data.dto.psychometric.imports;

/**
 * Non-file metadata accompanying an assessment-package import (multipart form fields).
 */
public record ImportPackageRequest(
        String testName,
        String description,
        com.edge.pulse.data.enums.TestType testType,
        Integer timeLimitSecs,
        String instrument
) {}
