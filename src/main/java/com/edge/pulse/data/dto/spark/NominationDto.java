package com.edge.pulse.data.dto.spark;

import com.edge.pulse.data.enums.NominationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record NominationDto(
        UUID id,
        UUID awardPeriodId,
        String awardPeriodName,
        SparkCategoryDto category,
        NomineeInfoDto nominee,
        // nominatorId/name is null when returned to entity leaders (anonymized)
        UUID nominatorId,
        String nominatorName,
        String justification,
        NominationStatus status,
        LocalDateTime submittedAt,
        List<AttachmentDto> attachments
) {}
