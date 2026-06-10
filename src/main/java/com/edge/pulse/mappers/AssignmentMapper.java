package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.AssignmentDto;
import com.edge.pulse.data.models.FormAssignment;
import org.springframework.stereotype.Component;

@Component
public class AssignmentMapper {

    public AssignmentDto toDto(FormAssignment a) {
        return new AssignmentDto(
                a.getId(),
                a.getForm().getId(),
                a.getForm().getTitle(),
                a.getOrgUnit() != null ? a.getOrgUnit().getId() : null,
                a.getOrgUnit() != null ? a.getOrgUnit().getOrgUnitName() : null,
                a.getUser() != null ? a.getUser().getId() : null,
                a.getUser() != null ? a.getUser().getDisplayName() : null,
                a.getAssignedBy().getId(),
                a.getAssignedBy().getDisplayName(),
                a.getAssignedAt(),
                a.getDueDate(),
                a.getStartsAt(),
                a.getExpiresAt(),
                a.isMandatory(),
                a.isActive(),
                a.isIncludeChildren(),
                a.isAllowResubmission(),
                a.getForm().getFormType().name()
        );
    }
}
