package com.edge.pulse.data.dto.spark;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

// Stub: storageUrl is always null — no actual upload performed
public record AddAttachmentRequest(
        @NotBlank String fileName,
        @NotBlank String fileType,
        @Positive long fileSize
) {}
