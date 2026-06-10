package com.edge.pulse.data.dto.spark;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttachmentDto(
        UUID id,
        String fileName,
        String fileType,
        long fileSize,
        // storageUrl is null until file upload is implemented
        String storageUrl,
        LocalDateTime uploadedAt
) {}
