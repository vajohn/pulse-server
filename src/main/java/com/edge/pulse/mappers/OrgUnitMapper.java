package com.edge.pulse.mappers;

import com.edge.pulse.data.dto.OrgTreeNodeDto;
import com.edge.pulse.data.models.OrganizationalUnit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class OrgUnitMapper {

    /**
     * Builds the org tree from a flat list of all active org units loaded in a single query.
     * Avoids N+1 queries that would occur with recursive lazy loading of children.
     *
     * <p>Accesses {@code unit.getParent().getId()} which Hibernate resolves from the proxy's
     * stored identifier without hitting the database — no extra queries fired.
     */
    public List<OrgTreeNodeDto> buildTree(List<OrganizationalUnit> allUnits) {
        Map<UUID, OrgTreeNodeDto> dtoMap = new LinkedHashMap<>();
        for (OrganizationalUnit unit : allUnits) {
            dtoMap.put(unit.getId(), new OrgTreeNodeDto(
                    unit.getId(), unit.getOrgUnitName(), unit.getOrgUnitCode(),
                    unit.getOrgLevel(), unit.getDepth(), new ArrayList<>()
            ));
        }

        List<OrgTreeNodeDto> roots = new ArrayList<>();
        for (OrganizationalUnit unit : allUnits) {
            OrgTreeNodeDto dto = dtoMap.get(unit.getId());
            if (unit.getParent() == null) {
                roots.add(dto);
            } else {
                OrgTreeNodeDto parentDto = dtoMap.get(unit.getParent().getId());
                if (parentDto != null) {
                    parentDto.children().add(dto);
                }
            }
        }
        return roots;
    }

    /** Single-unit mapping (kept for backward compatibility). */
    public OrgTreeNodeDto toTreeNode(OrganizationalUnit unit) {
        List<OrgTreeNodeDto> children = unit.getChildren() != null
                ? unit.getChildren().stream()
                    .filter(OrganizationalUnit::isActive)
                    .map(this::toTreeNode)
                    .toList()
                : List.of();

        return new OrgTreeNodeDto(
                unit.getId(), unit.getOrgUnitName(), unit.getOrgUnitCode(),
                unit.getOrgLevel(), unit.getDepth(), children
        );
    }
}
