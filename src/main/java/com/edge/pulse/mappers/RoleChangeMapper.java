package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.RoleChangeRequestDto;
import com.edge.pulse.data.models.RoleChangeRequest;
import org.springframework.stereotype.Component;

@Component
public class RoleChangeMapper {

    public RoleChangeRequestDto toDto(RoleChangeRequest r) {
        return new RoleChangeRequestDto(
                r.getId(),
                r.getTargetUser().getId(),
                r.getTargetUser().getDisplayName(),
                r.getRequestedBy().getId(),
                r.getRequestedBy().getDisplayName(),
                r.getRoleName(),
                r.getAction(),
                r.getStatus(),
                r.getReviewedBy() != null ? r.getReviewedBy().getId() : null,
                r.getReviewedBy() != null ? r.getReviewedBy().getDisplayName() : null,
                r.getReviewComment(),
                r.getCreatedAt(),
                r.getReviewedAt()
        );
    }
}
